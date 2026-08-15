package com.example.obituarymaker.obituary;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ObituaryService {

    private static final Logger log = LoggerFactory.getLogger(ObituaryService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GoogleSheetsClient sheetsClient;
    private final S3Publisher publisher;
    private final Validator validator;

    public ObituaryService(GoogleSheetsClient sheetsClient, S3Publisher publisher, Validator validator) {
        this.sheetsClient = sheetsClient;
        this.publisher = publisher;
        this.validator = validator;
    }

    /**
     * S3 업로드를 시트 기록보다 먼저 한다.
     * 반대로 하면 S3 실패 시 시트에 열리지 않는 링크가 남아 운영자가 정상 건과 구분할 수 없다.
     */
    public String create(Obituary obituary) throws IOException {
        obituary.setId(newId());
        obituary.setCreatedAt(now());
        obituary.setUpdatedAt("");
        obituary.setShareUrl(publisher.shareUrl(obituary.getId()));

        publisher.publish(obituary);
        sheetsClient.append(obituary);
        notifyAdmin(obituary);

        return obituary.getId();
    }

    /**
     * 시트 갱신을 S3 업로드보다 먼저 한다.
     * 시트가 원본이므로 S3에서 실패하면 같은 요청을 다시 보내 맞출 수 있다.
     */
    public void update(String id, Map<String, String> changes) throws IOException {
        Obituary obituary = sheetsClient.findById(id)
                .orElseThrow(() -> new NoSuchElementException("부고장을 찾을 수 없습니다."));

        obituary.applyChanges(changes);
        validate(obituary);
        obituary.setUpdatedAt(now());

        sheetsClient.update(obituary);
        publisher.publish(obituary);
    }

    public String shareUrl(String id) {
        return publisher.shareUrl(id);
    }

    private void validate(Obituary obituary) {
        Set<ConstraintViolation<Obituary>> violations = validator.validate(obituary);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(" ")));
        }
    }

    private void notifyAdmin(Obituary obituary) {
        log.info("""
                새 부고장이 만들어졌습니다.
                  고인   : {}
                  상주   : {} ({})
                  링크   : {}
                  수정   : curl -X PATCH $APP_URL/admin/obituaries/{} -H "X-Admin-Token: $ADMIN_TOKEN" -d "room=5호실"\
                """,
                obituary.getName(), obituary.getMournerName(), obituary.getMournerPhone(),
                obituary.getShareUrl(), obituary.getId());
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String now() {
        return LocalDateTime.now(KST).format(TIMESTAMP);
    }
}
