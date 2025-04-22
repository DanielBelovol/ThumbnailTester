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

    @Override
    public String toString() {
        return "StatsRequest{" +
                "videoUrl='" + videoUrl + '\'' +
                ", views=" + views +
                ", ctr=" + ctr +
                ", impressions=" + impressions +
                ", averageViewDuration=" + averageViewDuration +
                ", advCtr=" + advCtr +
                ", comments=" + comments +
                ", shares=" + shares +
                ", likes=" + likes +
                ", subscribersGained=" + subscribersGained +
                ", averageViewPercentage=" + averageViewPercentage +
                ", totalWatchTime=" + totalWatchTime +
                ", active=" + active +
                '}';
    }
}

