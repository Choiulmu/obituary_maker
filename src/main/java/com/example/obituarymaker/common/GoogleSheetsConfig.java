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
import java.util.List;

@Configuration
public class GoogleSheetsConfig {

    @Bean
    public Sheets sheets(@Value("${google-credentials:}") String credentialsJson) throws Exception {
        if (credentialsJson.isBlank()) {
            throw new IllegalStateException("Parameter Store의 /obituary/google-credentials 값이 없습니다.");
        }
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
