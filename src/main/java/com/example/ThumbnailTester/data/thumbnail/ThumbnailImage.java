package com.example.ThumbnailTester.data.thumbnail;

import lombok.Data;

@Data
public class ThumbnailImage {
    String fileBase64;
    ThumbnailStats thumbnailStats;
    boolean isWinner;
}
