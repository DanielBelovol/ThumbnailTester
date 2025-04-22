package com.example.ThumbnailTester.config;


import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;

import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtubeAnalytics.YouTubeAnalytics;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class YouTubeAnalyticsConfig {
    @Value("${youtube.access.token}")
    private String accessToken;

    @Value("${application.name}")
    private String applicationName;

    @Bean
    public YouTubeAnalytics youtubeAnalytics() throws Exception {
        // HttpTransport: Used for making HTTP requests to the API
        HttpTransport httpTransport = new NetHttpTransport();

        // JsonFactory: Used for handling JSON
        JsonFactory jsonFactory = new JacksonFactory();

        // OAuth 2.0 Authentication
        GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);

        // YouTube Analytics API client setup
        YouTubeAnalytics youtubeAnalytics = new YouTubeAnalytics.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(applicationName)
                .build();

        return youtubeAnalytics;
    }
}
