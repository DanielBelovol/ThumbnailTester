package com.example.ThumbnailTester.config;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class YouTubeConfig {

    @Bean
    public YouTube youtube() throws Exception {
        // Настройка HTTP транспорт
        HttpTransport httpTransport = new NetHttpTransport();

        // Настройка JSON фабрики
        JsonFactory jsonFactory = new JacksonFactory();

        // Настройка аутентификации
        GoogleCredential credential = new GoogleCredential().setAccessToken("YOUR_ACCESS_TOKEN");

        // Создание YouTube API клиента
        YouTube youtube = new YouTube.Builder(httpTransport, jsonFactory, new HttpRequestInitializer() {
            @Override
            public void initialize(com.google.api.client.http.HttpRequest httpRequest) throws IOException, IOException {
                credential.initialize(httpRequest);
            }
        }).setApplicationName("YourApplicationName").build();

        return youtube;
    }
}
