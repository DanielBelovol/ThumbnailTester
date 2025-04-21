package com.example.ThumbnailTester.Request;

import lombok.Data;

@Data
public class StatsRequest {
    private String videoUrl;

    private Integer views;
    private Double ctr;
    private Integer impressions;
    private Double averageViewDuration;
    private Double advCtr;
    private Integer comments;
    private Integer shares;
    private Integer likes;
    private Integer subscribersGained;
    private Double averageViewPercentage;
    private Long totalWatchTime;

    private Boolean active;
}

