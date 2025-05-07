package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    @Value("${youtube.client.id}")
    private String clientId;

    @Value("${youtube.client.secret}")
    private String clientSecret;

    @Value("${application.name}")
    private String applicationName;
    private static final String APPLICATION_NAME = "YOUR_APPLICATION_NAME";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();


    public YouTubeAnalyticsService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public ThumbnailStats getStats(UserData user, LocalDate startDate, ThumbnailQueueItem thumbnailQueueItem) {
        try {
            if (thumbnailQueueItem == null) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "Нет активной миниатюры для пользователя");
                return null;
            }

            // Формируем запрос
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://youtubeanalytics.googleapis.com/v2/reports" +
                            "?ids=channel==MINE" +
                            "&startDate=" + startDate.toString() +
                            "&endDate="+ LocalDate.now().toString() +
                            "&metrics=views,impressions,averageViewDuration,comments,shares,likes,subscribersGained,averageViewPercentage,estimatedMinutesWatched" + // Добавляем метрики
                            "&dimensions=video" +
                            "&filters=video==" + extractVideoIdFromUrl(thumbnailQueueItem.getVideoUrl())))
                    .header("Authorization", "Bearer " + refreshAccessToken(user.getRefreshToken()))
                    .GET()
                    .build();

            // Отправляем запрос и получаем ответ
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Обрабатываем ответ
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseJson = mapper.readTree(response.body());

            // Получаем данные из ответа
            JsonNode rows = responseJson.path("rows");
            if (rows.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "Нет данных по видео");
                ThumbnailStats emptyStats = new ThumbnailStats();
                fillEmptyStats(emptyStats,thumbnailQueueItem);
                return emptyStats;
            }

            JsonNode row = rows.get(0);

            // Получаем значения метрик
            Integer views = toInt(row.path(0)); // Количество просмотров
            Integer impressions = toInt(row.path(1)); // Количество показов
            Double averageViewDuration = toDouble(row.path(2)); // Средняя продолжительность просмотра
            Integer comments = toInt(row.path(3)); // Количество комментариев
            Integer shares = toInt(row.path(4)); // Количество расшариваний
            Integer likes = toInt(row.path(5)); // Количество лайков
            Integer subscribersGained = toInt(row.path(6)); // Количество подписчиков
            Double averageViewPercentage = toDouble(row.path(7)); // Средний процент просмотра
            Long totalWatchTime = toLong(row.path(8)); // Общее время просмотра (в минутах)

            // Рассчитываем CTR (Click-Through Rate), если нужно
            Double ctr = calculateCTR(impressions, views); // Здесь ты можешь использовать формулу CTR, если необходимо

            // Создаем объект ThumbnailStats и заполняем поля

            //old stats
            ThumbnailStats oldStats = thumbnailQueueItem.getImageOption().getThumbnailStats();
            if (oldStats == null) {
                oldStats = fillEmptyStats(new ThumbnailStats(), thumbnailQueueItem); // или как тебе логичнее: пропустить diff, выкинуть ошибку, инициализировать и т.д.
            }



            //new stats
            ThumbnailStats newStats = new ThumbnailStats();
            if (oldStats != null) {
                newStats.setId(oldStats.getId());
            }

            newStats.setViews(views);
            newStats.setImpressions(impressions);
            newStats.setAverageViewDuration(averageViewDuration);
            newStats.setComments(comments);
            newStats.setShares(shares);
            newStats.setLikes(likes);
            newStats.setSubscribersGained(subscribersGained);
            newStats.setAverageViewPercentage(averageViewPercentage);
            newStats.setTotalWatchTime(totalWatchTime);

            ThumbnailStats currentStats = calculateStatsDifference(newStats, oldStats);
            thumbnailQueueItem.getImageOption().setThumbnailStats(currentStats);

            // Возвращаем статистику
            return currentStats;
        } catch (IOException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Ошибка при получении данных YouTube Analytics: " + e.getMessage());
            log.error("error ioexc:"+e.getMessage());
        } catch (InterruptedException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Ошибка при получении данных YouTube Analytics: " + e.getMessage());
            log.error("error inter:"+e.getMessage());
        }
        ThumbnailStats emptyStats = new ThumbnailStats();
        fillEmptyStats(emptyStats, thumbnailQueueItem);
        return emptyStats;
    }

    // Формула для вычисления CTR (Click-Through Rate)
    private Double calculateCTR(Integer impressions, Integer views) {
        if (impressions == null || impressions == 0 || views == null || views == 0) {
            return 0.0; // Если нет данных, возвращаем 0%
        }
        return (double) views / impressions * 100; // CTR = (views / impressions) * 100
    }

    public String refreshAccessToken(String refreshToken) {
        try {
            log.info("entering to refresh token method");
            String requestBody = "client_id=" + clientId
                    + "&client_secret=" + clientSecret
                    + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                    + "&grant_type=refresh_token";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("response is received:"+ response.body());

            // Используем Jackson для разбора JSON-ответа
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(response.body());

            if (jsonNode.has("access_token")) {
                return jsonNode.get("access_token").asText();
            } else {
                log.error("Ошибка получения access token: " + response.body());
                return null;
            }
        } catch (IOException e) {
            log.error("IOException:" + e.getMessage());
        } catch (InterruptedException e) {
            log.error("InterruptedException:" + e.getMessage());
        }
        return null;
    }
    public ThumbnailStats calculateStatsDifference(ThumbnailStats later, ThumbnailStats earlier) {
        ThumbnailStats diff = new ThumbnailStats();
        diff.setViews(safeSubtract(later.getViews(), earlier.getViews()));
        diff.setImpressions(safeSubtract(later.getImpressions(), earlier.getImpressions()));
        diff.setAverageViewDuration(later.getAverageViewDuration()); // average, keep latest or calculate weighted average
        diff.setComments(safeSubtract(later.getComments(), earlier.getComments()));
        diff.setShares(safeSubtract(later.getShares(), earlier.getShares()));
        diff.setLikes(safeSubtract(later.getLikes(), earlier.getLikes()));
        diff.setSubscribersGained(safeSubtract(later.getSubscribersGained(), earlier.getSubscribersGained()));
        diff.setAverageViewPercentage(later.getAverageViewPercentage()); // average, keep latest or calculate weighted average
        diff.setTotalWatchTime(safeSubtractLong(later.getTotalWatchTime(), earlier.getTotalWatchTime()));
        diff.setCtr(calculateCTR(diff.getImpressions(), diff.getViews()));
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
        if (node.isNull()) return null;
        return node.asLong();
    }
    private ThumbnailStats fillEmptyStats(ThumbnailStats stats, ThumbnailQueueItem thumbnailQueueItem) {
        stats.setViews(0);
        stats.setImpressions(0);
        stats.setAverageViewDuration(0.0);
        stats.setComments(0);
        stats.setShares(0);
        stats.setLikes(0);
        stats.setSubscribersGained(0);
        stats.setAverageViewPercentage(0.0);
        stats.setTotalWatchTime(0L);
        stats.setCtr(0.0);

        stats.setImageOption(thumbnailQueueItem.getImageOption());
        return stats;
    }

}
