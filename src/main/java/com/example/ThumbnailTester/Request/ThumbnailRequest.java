package com.example.ThumbnailTester.Request;

import com.example.ThumbnailTester.dto.ImageOption;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
@NoArgsConstructor
public class ThumbnailRequest {
    private List<ImageOption> imageOptions;
    private String videoUrl;
    private ThumbnailTestConfRequest testConfRequest;
    private UserRequest userDTO;
}
