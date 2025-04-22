package com.example.ThumbnailTester.Request;

import com.example.ThumbnailTester.dto.ImageOption;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ThumbnailRequest {
    private List<ImageOption> imageOptions;
    private List<String> texts;
    private String videoUrl;
    private ThumbnailTestConfRequest testConfRequest;
    private UserRequest userDTO;
}
