package com.example.ThumbnailTester.Request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ThumbnailRequest {
    private List<String> images;
    private List<String> texts;
    private String videoUrl;
    private ThumbnailTestConfRequest testConfRequest;
    private UserRequest userDTO;
}
