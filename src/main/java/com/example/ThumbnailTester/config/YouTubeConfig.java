package com.example.ThumbnailTester.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class YouTubeConfig {
    @Value("${youtube.access.token}")
    private String ACCESS_TOKEN;

    @Value("${application.name}")
    private String APPLICATION_NAME;

    @Bean
    public YouTube youtube() throws Exception {
        HttpTransport httpTransport = new NetHttpTransport();

        JsonFactory jsonFactory = new JacksonFactory();

        GoogleCredential credential = new GoogleCredential().setAccessToken(ACCESS_TOKEN);

        YouTube youtube = new YouTube.Builder(httpTransport, jsonFactory, new HttpRequestInitializer() {
            @Override
            public void initialize(com.google.api.client.http.HttpRequest httpRequest) throws IOException, IOException {
                credential.initialize(httpRequest);
            }
        }).setApplicationName(APPLICATION_NAME).build();

        return youtube;
    }
}
