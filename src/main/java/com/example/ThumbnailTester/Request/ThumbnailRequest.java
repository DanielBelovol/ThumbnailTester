package com.example.ThumbnailTester.Request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
@NoArgsConstructor
public class ThumbnailRequest {
    private List<String> fileBase64;
    private String videoUrl;
    private ThumbnailTestConfRequest testConfRequest;
    private UserRequest userDTO;
}
