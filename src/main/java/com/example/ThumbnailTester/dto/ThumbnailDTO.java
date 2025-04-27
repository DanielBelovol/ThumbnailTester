package com.example.ThumbnailTester.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ThumbnailDTO {
    private long id;
    private List<ImageOption> images;
    private String videoUrl;
}
