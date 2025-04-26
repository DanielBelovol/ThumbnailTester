package com.example.ThumbnailTester.dto;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class ThumbnailQueueItem {
    private String videoUrl;
    private ImageOption imageOption;
    boolean isActive;

    public ThumbnailQueueItem(String videoUrl, ImageOption imageOption) {
        this.videoUrl = videoUrl;
        this.imageOption = imageOption;
        this.isActive = false;
    }
}
