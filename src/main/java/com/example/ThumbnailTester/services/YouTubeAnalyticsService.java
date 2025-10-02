package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import com.example.ThumbnailTester.util.AESUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Service
public class YouTubeAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(YouTubeAnalyticsService.class);

    private final SimpMessagingTemplate messagingTemplate;
    @Autowired
    private AESUtil aesUtil;

    @Value("${youtube.client.id}")
    private String clientId;

    @Value("${youtube.client.secret}")
    private String clientSecret;

    @Value("${application.name}")
    private String applicationName;

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static final String YOUTUBE_ANALYTICS_API_URL = "https://youtubeanalytics.googleapis.com/v2/reports";
    private static final String OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";

    private static final String TOPIC_ERROR = "/topic/thumbnail/error";

    private static final String ERR_NO_ACTIVE_THUMBNAIL = "No active thumbnail found for user";
    private static final String ERR_FAILED_REFRESH_TOKEN = "Failed to refresh access token";
    private static final String ERR_NO_DATA_FOR_VIDEO = "No data available for video";
    private static final String ERR_RETRIEVING_ANALYTICS = "Error retrieving YouTube Analytics data: ";
    private static final String ERR_FAILED_ACCESS_TOKEN = "Failed to obtain access token";

    public YouTubeAnalyticsService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public ThumbnailStats getStats(UserData user, LocalDate startDate, ThumbnailQueueItem thumbnailQueueItem) {
        if (thumbnailQueueItem == null) {
            sendError(ERR_NO_ACTIVE_THUMBNAIL);
            return null;
        }

        try {
            String videoId = extractVideoIdFromUrl(thumbnailQueueItem.getVideoUrl());
            String accessToken = refreshAccessToken(aesUtil.decrypt(user.getRefreshToken()));
            if (accessToken == null) {
                sendError(ERR_FAILED_REFRESH_TOKEN);
                return fillEmptyStats(new ThumbnailStats(), thumbnailQueueItem);
            }

            String uri = String.format(
                    "%s?ids=channel==MINE&startDate=%s&endDate=%s&metrics=views,averageViewDuration,comments,shares,likes,subscribersGained,averageViewPercentage,estimatedMinutesWatched&dimensions=video&filters=video==%s",
                    YOUTUBE_ANALYTICS_API_URL, startDate, LocalDate.now(), videoId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseJson = mapper.readTree(response.body());

            JsonNode rows = responseJson.path("rows");
            if (rows.isEmpty()) {
                sendError(ERR_NO_DATA_FOR_VIDEO);
                ThumbnailStats emptyStats = new ThumbnailStats();
                fillEmptyStats(emptyStats, thumbnailQueueItem);
                return emptyStats;
            }

            JsonNode row = rows.get(0);

            Integer views = toInt(row.path(0));
            Double averageViewDuration = toDouble(row.path(1));
            Integer comments = toInt(row.path(2));
            Integer shares = toInt(row.path(3));
            Integer likes = toInt(row.path(4));
            Integer subscribersGained = toInt(row.path(5));
            Double averageViewPercentage = toDouble(row.path(6));
            Long totalWatchTime = toLong(row.path(7));

            ThumbnailStats oldStats = thumbnailQueueItem.getImageOption().getThumbnailStats();
            if (oldStats == null) {
                oldStats = fillEmptyStats(new ThumbnailStats(), thumbnailQueueItem);
            }

            ThumbnailStats newStats = new ThumbnailStats();
            if (oldStats != null) {
                newStats.setId(oldStats.getId());
            }
            newStats.setViews(views);
            newStats.setAverageViewDuration(averageViewDuration);
            newStats.setComments(comments);
            newStats.setShares(shares);
            newStats.setLikes(likes);
            newStats.setSubscribersGained(subscribersGained);
            newStats.setAverageViewPercentage(averageViewPercentage);
            newStats.setTotalWatchTime(totalWatchTime);

            ThumbnailStats currentStats = calculateStatsDifference(newStats, oldStats);
            thumbnailQueueItem.getImageOption().setThumbnailStats(currentStats);

            return currentStats;

        } catch (IOException | InterruptedException e) {
            sendError(ERR_RETRIEVING_ANALYTICS + e.getMessage());
            log.error("Error fetching YouTube Analytics data", e);
        } catch (Exception e) {
            log.error("Error decoding refresh token");
            throw new RuntimeException(e);
        }

        ThumbnailStats emptyStats = new ThumbnailStats();
        fillEmptyStats(emptyStats, thumbnailQueueItem);
        return emptyStats;
    }

    public String refreshAccessToken(String refreshToken) {
        try {
            log.info("Refreshing access token");
            String requestBody = "client_id=" + clientId
                    + "&client_secret=" + clientSecret
                    + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                    + "&grant_type=refresh_token";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OAUTH_TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Received token refresh response: {}", response.body());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(response.body());

            if (jsonNode.has("access_token")) {
                return jsonNode.get("access_token").asText();
            } else {
                log.error(ERR_FAILED_ACCESS_TOKEN + ": {}", response.body());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            log.error("Exception during token refresh: {}", e.getMessage(), e);
            return null;
        }
    }

    public ThumbnailStats calculateStatsDifference(ThumbnailStats later, ThumbnailStats earlier) {
        ThumbnailStats diff = new ThumbnailStats();
        diff.setViews(safeSubtract(later.getViews(), earlier.getViews()));
        diff.setAverageViewDuration(later.getAverageViewDuration());
        diff.setComments(safeSubtract(later.getComments(), earlier.getComments()));
        diff.setShares(safeSubtract(later.getShares(), earlier.getShares()));
        diff.setLikes(safeSubtract(later.getLikes(), earlier.getLikes()));
        diff.setSubscribersGained(safeSubtract(later.getSubscribersGained(), earlier.getSubscribersGained()));
        diff.setAverageViewPercentage(later.getAverageViewPercentage());
        diff.setTotalWatchTime(safeSubtractLong(later.getTotalWatchTime(), earlier.getTotalWatchTime()));
        return diff;
    }

    private Integer safeSubtract(Integer a, Integer b) {
        if (a == null || b == null) return null;
        return Math.max(0, a - b);
    }

    private Long safeSubtractLong(Long a, Long b) {
        if (a == null || b == null) return null;
        return Math.max(0L, a - b);
    }

    private String extractVideoIdFromUrl(String url) {
        if (url.contains("v=")) {
            return url.split("v=")[1].split("&")[0];
        }
        return url;
    }

    private Integer toInt(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.asInt();
    }

    private Double toDouble(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.asDouble();
    }

    private Long toLong(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.asLong();
    }

    private ThumbnailStats fillEmptyStats(ThumbnailStats stats, ThumbnailQueueItem thumbnailQueueItem) {
        stats.setViews(0);
        stats.setAverageViewDuration(0.0);
        stats.setComments(0);
        stats.setShares(0);
        stats.setLikes(0);
        stats.setSubscribersGained(0);
        stats.setAverageViewPercentage(0.0);
        stats.setTotalWatchTime(0L);

        stats.setImageOption(thumbnailQueueItem.getImageOption());
        return stats;
    }

    private void sendError(String message) {
        log.error(message);
        messagingTemplate.convertAndSend(TOPIC_ERROR, message);
    }
}
