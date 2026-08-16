package com.example.obituarymaker.obituary;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class S3PublisherTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3Publisher publisher =
            new S3Publisher(s3Client, templateEngine(), "obituary-bucket", "https://obituary.example.com");

    private static TemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private Obituary sample() {
        Obituary obituary = new Obituary();
        obituary.setId("a1b2c3d4");
        obituary.setName("홍길동");
        obituary.setDeathDate(LocalDate.of(2026, 8, 14));
        obituary.setFuneralHome("서울추모공원");
        obituary.setRoom("3호실");
        obituary.setDepartureDate(LocalDate.of(2026, 8, 16));
        obituary.setMournerName("홍철수");
        obituary.setMournerPhone("010-1234-5678");
        return obituary;
    }

    @Test
    void 공유_링크는_업로드한_객체_주소와_같다() {
        assertThat(publisher.shareUrl("a1b2c3d4")).isEqualTo("https://obituary.example.com/a1b2c3d4/index.html");
    }

    @Test
    void 부고장을_아이디_폴더의_index_html로_올린다() throws IOException {
        publisher.publish(sample());

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(request.capture(), body.capture());

        assertThat(request.getValue().bucket()).isEqualTo("obituary-bucket");
        assertThat(request.getValue().key()).isEqualTo("a1b2c3d4/index.html");
        assertThat(request.getValue().contentType()).isEqualTo("text/html; charset=UTF-8");

        String html = new String(body.getValue().contentStreamProvider().newStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(html)
                .contains("故 홍길동")
                .contains("2026년 8월 14일,")
                .contains("서울추모공원")
                .contains("3호실")
                .contains("2026년 8월 16일")
                .contains("tel:010-1234-5678")
                .doesNotContain("조의금 안내")
                .doesNotContain("지도에서 길 찾기");
    }
}
