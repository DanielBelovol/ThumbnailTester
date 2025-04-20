package com.example.ThumbnailTester.result;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import lombok.Data;

@Data
public class ImageResult {
    long imageIndex;
    String fileBase64;
    ThumbnailStats thumbnailStats;
    boolean isWinner;
}
