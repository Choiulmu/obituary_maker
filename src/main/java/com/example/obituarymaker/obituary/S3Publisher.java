package com.example.obituarymaker.obituary;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Component
public class S3Publisher {

    private final S3Client s3Client;
    private final TemplateEngine templateEngine;
    private final String bucket;
    private final String publicBaseUrl;

    public S3Publisher(S3Client s3Client, TemplateEngine templateEngine,
                       @Value("${s3-bucket}") String bucket,
                       @Value("${public-base-url}") String publicBaseUrl) {
        this.s3Client = s3Client;
        this.templateEngine = templateEngine;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * S3 REST 엔드포인트는 디렉터리 인덱스를 해주지 않아 `index.html`까지 붙인다.
     * 나중에 CloudFront를 앞에 두면 `/{id}/`로 줄일 수 있다.
     */
    public String shareUrl(String id) {
        return publicBaseUrl + "/" + id + "/index.html";
    }

    public void publish(Obituary obituary) {
        String html = templateEngine.process("obituary/view",
                new Context(Locale.KOREA, Map.of("obituary", obituary)));

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(obituary.getId() + "/index.html")
                        .contentType("text/html; charset=UTF-8")
                        .build(),
                RequestBody.fromString(html, StandardCharsets.UTF_8));
    }
}
