package com.example.obituarymaker.common;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Configuration
public class GoogleSheetsConfig {

    //TODO: 서버 시작할 때마다 매번 Sheets를 만드나요?

    @Value("${google-credentials}")
    private String googleCredentials;

    @Bean
    public Sheets sheets() throws Exception {
        if (googleCredentials.isBlank()) {
            throw new IllegalStateException("google-credentials 값이 없습니다. 운영은 Parameter Store(/obituary/google-credentials), 로컬은 .env를 확인한다.");
        }

        // properties 형식인 .env에는 여러 줄 JSON을 넣을 수 없어서, 로컬은 키 파일 경로를 적는다.
        String credentialsJson = googleCredentials.startsWith("{")
                ? googleCredentials
                : Files.readString(Path.of(googleCredentials));

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)))
                .createScoped(List.of(SheetsScopes.SPREADSHEETS));

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("obituary-maker")
                .build();
    }
}
