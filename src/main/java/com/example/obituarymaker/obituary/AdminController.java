package com.example.obituarymaker.obituary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/admin/obituaries")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final ObituaryService obituaryService;
    private final String adminToken;

    public AdminController(ObituaryService obituaryService, @Value("${admin-token:}") String adminToken) {
        this.obituaryService = obituaryService;
        this.adminToken = adminToken;
    }

    @PatchMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable String id,
                                         @RequestHeader(name = "X-Admin-Token", required = false) String token,
                                         @RequestParam Map<String, String> changes) {
        if (!hasValidToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("권한이 없습니다.");
        }
        try {
            obituaryService.update(id, changes);
            return ResponseEntity.ok("수정했습니다.");
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("부고장 수정 실패: {}", id, e);
            return ResponseEntity.internalServerError().body("수정하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private boolean hasValidToken(String token) {
        if (adminToken.isBlank() || token == null) {
            return false;
        }
        return MessageDigest.isEqual(adminToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
