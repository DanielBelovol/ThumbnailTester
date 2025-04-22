package com.example.ThumbnailTester.dto;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ThumbnailDTO {
    private long id;
    private List<ImageOption> images;
    private String videoUrl;
}
