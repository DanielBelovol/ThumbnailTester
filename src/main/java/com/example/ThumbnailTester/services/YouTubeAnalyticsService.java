package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import com.example.ThumbnailTester.data.user.UserData;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtubeAnalytics.YouTubeAnalytics;
import com.google.api.services.youtubeAnalytics.model.ResultTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class YouTubeAnalyticsService {
    private static final String APPLICATION_NAME = "YourApplicationName";
    @Autowired
    private YouTube youTube;
    @Autowired
    private YouTubeAnalytics youTubeAnalytics;
    @Autowired
    private ThumbnailStatsService thumbnailStatsService;
    @Autowired
    private UserService userService;
    @Autowired
    private ThumbnailService thumbnailService;

    public void fetchAndSaveStats(UserData user) throws IOException {
        // Получаем активную миниатюру
        ThumbnailData activeThumbnail = thumbnailService.getActiveThumbnailByUser(user.getId());
        if (activeThumbnail == null) {
            System.out.println("Нет активной миниатюры для пользователя");
            return;
        }

        String videoId = extractVideoIdFromUrl(activeThumbnail.getVideoUrl());

        // Выполняем запрос к YouTube Analytics API
        ResultTable analyticsResponse = youTubeAnalytics.reports()
                .query("channel==MINE", "2024-01-01", "2025-12-31",
                        "views,comments,likes,subscribersGained,shares,estimatedMinutesWatched,averageViewDuration,averageViewPercentage,impressions,impressionsClickThroughRate")
                .setFilters("video==" + videoId)
                .execute();

        // Разбор результата
        if (analyticsResponse.getRows() == null || analyticsResponse.getRows().isEmpty()) {
            System.out.println("Нет данных для видео: " + videoId);
            return;
        }

        List<Object> data = analyticsResponse.getRows().get(0);
        ThumbnailStats stats = new ThumbnailStats();
        stats.setThumbnail(activeThumbnail);
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
        stats.setAdvCtr(null); // Расчётный, если хочешь — можно вывести отдельно

        thumbnailStatsService.save(stats);
    }

    private String extractVideoIdFromUrl(String url) {
        // Простой способ вытащить ID из https://www.youtube.com/watch?v=VIDEO_ID
        if (url.contains("v=")) {
            return url.split("v=")[1].split("&")[0];
        }
        return url; // fallback
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
