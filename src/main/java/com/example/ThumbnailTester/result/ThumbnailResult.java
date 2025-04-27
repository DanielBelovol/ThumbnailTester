package com.example.ThumbnailTester.result;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailTestConf;
import com.example.ThumbnailTester.data.user.UserData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThumbnailResult {
    private List<ImageResult> imageResultList;
    private String videoUrl;
    private ThumbnailTestConf testConf;
    private UserData user;
}
