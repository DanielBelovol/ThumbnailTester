package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import com.example.ThumbnailTester.mapper.Mapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtubeAnalytics.YouTubeAnalytics;
import com.google.api.services.youtubeAnalytics.model.ResultTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.List;

@Service
public class YouTubeAnalyticsService {
    private final SimpMessagingTemplate messagingTemplate;
    @Value("${youtube.client.id}")
    private String clientId;

    @Value("${youtube.client.secret}")
    private String clientSecret;

    @Value("${application.name}")
    private String applicationName;

    @Autowired
    private Mapper mapper;

    public YouTubeAnalyticsService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public ThumbnailStats getStats(UserData user, LocalDate startDate, ThumbnailQueueItem thumbnailQueueItem) {
        try {
            if (thumbnailQueueItem == null) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "No active thumbnail for the user");
                return null;
            }

            String videoId = extractVideoIdFromUrl(thumbnailQueueItem.getVideoUrl());

            YouTubeAnalytics analytics = buildAnalyticsServiceFromUser(user);
            if (analytics == null) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "Failed to build YouTube Analytics service.");
                return null;
            }

            ResultTable analyticsResponse = analytics.reports()
                    .query("channel==MINE", startDate.toString(), "2025-12-31",
                            "views,comments,likes,subscribersGained,shares,estimatedMinutesWatched,averageViewDuration,averageViewPercentage,impressions,impressionsClickThroughRate")
                    .setFilters("video==" + videoId)
                    .execute();

            if (analyticsResponse.getRows() == null || analyticsResponse.getRows().isEmpty()) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "No data for this video: " + videoId);
                return null;
            }

            List<Object> data = analyticsResponse.getRows().get(0);

            ThumbnailStats stats = new ThumbnailStats();
            stats.setImageOption(mapper.thumbnailDataToImageOption(thumbnailQueueItem));
            stats.setViews(toInt(data.get(0)));
            stats.setComments(toInt(data.get(1)));
            stats.setLikes(toInt(data.get(2)));
            stats.setSubscribersGained(toInt(data.get(3)));
            stats.setShares(toInt(data.get(4)));
            stats.setTotalWatchTime((Long) data.get(5));
            stats.setAverageViewDuration(toDouble(data.get(6)));
            stats.setAverageViewPercentage(toDouble(data.get(7)));
            stats.setImpressions(toInt(data.get(8)));
            stats.setCtr(toDouble(data.get(9)));

            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, LocalDate.now());
            stats.setAdvCtr(daysBetween > 0 ? (double) stats.getTotalWatchTime() / daysBetween : 0.0);

            return stats;
        } catch (IOException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Error updating video title: " + e.getMessage());
        }
        return null;
    }

    private YouTubeAnalytics buildAnalyticsServiceFromUser(UserData user) {
        try {
            var credential = new GoogleCredential.Builder()
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(JacksonFactory.getDefaultInstance())
                    .setClientSecrets(clientId, clientSecret)
                    .build()
                    .setRefreshToken(user.getRefreshToken());

            credential.refreshToken();

            return new YouTubeAnalytics.Builder(
                    credential.getTransport(),
                    credential.getJsonFactory(),
                    credential
            ).setApplicationName(applicationName).build();
        } catch (IOException | GeneralSecurityException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Error building YouTube Analytics service: " + e.getMessage());
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Unexpected error: " + e.getMessage());
        }
        return null;
    }


    private String extractVideoIdFromUrl(String url) {
        if (url.contains("v=")) {
            return url.split("v=")[1].split("&")[0];
        }
        return url;
    }

    private Integer toInt(Object obj) {
        if (obj == null) return null;
        return ((Number) obj).intValue();
    }

    private Double toDouble(Object obj) {
        if (obj == null) return null;
        return ((Number) obj).doubleValue();
    }
}
