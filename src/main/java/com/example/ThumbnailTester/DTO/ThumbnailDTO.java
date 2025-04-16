package com.example.ThumbnailTester.DTO;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailTestConf;
import com.example.ThumbnailTester.data.user.UserData;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@NoArgsConstructor
public class ThumbnailDTO {
    private String fileBase64;
    private String videoUrl;
    private ThumbnailTestConfDTO testConfDTO;
    private UserDTO userDTO;
}
