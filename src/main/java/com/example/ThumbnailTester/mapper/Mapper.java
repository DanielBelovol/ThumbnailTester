package com.example.ThumbnailTester.mapper;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.Request.ThumbnailTestConfRequest;
import com.example.ThumbnailTester.data.thumbnail.*;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ImageOption;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import com.example.ThumbnailTester.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class Mapper {
    @Autowired
    private UserService userService;

    public ThumbnailTestConf testConfRequestToDTO(ThumbnailTestConfRequest request) {
        ThumbnailTestConf testConf = new ThumbnailTestConf();

        testConf.setTestType(TestConfType.valueOf(request.getTestType()));
        testConf.setTestingType(TestingType.valueOf(request.getTestingType()));
        testConf.setTestingByTimeMinutes(request.getTestingByTimeMinutes());
        testConf.setTestingByMetrics(request.getTestingByMetrics());
        testConf.setCriterionOfWinner(CriterionOfWinner.valueOf(request.getCriterionOfWinner()));

        return testConf;
    }

    public ThumbnailData thumbnailRequestToData(ThumbnailRequest thumbnailRequest) {
        UserData userData = userService.getByGoogleId(thumbnailRequest.getUserDTO().getGoogleId());

        ThumbnailData thumbnailData = new ThumbnailData();
        thumbnailData.setVideoUrl(thumbnailRequest.getVideoUrl());
        thumbnailData.setTestConf(testConfRequestToDTO(thumbnailRequest.getTestConfRequest()));

        if (userService.isExistById(userData.getId())) {
            thumbnailData.setUser(userData);
        } else {
            thumbnailData.setUser(new UserData(
                    thumbnailRequest.getUserDTO().getGoogleId(),
                    thumbnailRequest.getUserDTO().getRefreshToken()));
        }

        // Теперь создаём imageOptions после создания thumbnailData
        List<ImageOption> imageOptions = listBase64ToImageOptionList(
                thumbnailRequest.getImages(), thumbnailData);

        thumbnailData.setImageOptions(imageOptions);

        return thumbnailData;
    }


    public ImageOption thumbnailDataToImageOption(ThumbnailQueueItem thumbnailQueueItem) {
        return thumbnailQueueItem.getImageOption();
    }
    public ImageOption imageToImageOption(String fileBase64, ThumbnailData thumbnailData) {
        ImageOption imageOption1 = new ImageOption();
        imageOption1.setText(null);
        imageOption1.setFileBase64(fileBase64);
        imageOption1.setThumbnail(thumbnailData);
        imageOption1.setWinner(false);
        imageOption1.setThumbnailStats(null);
        return imageOption1;
    }

    public List<ImageOption> listBase64ToImageOptionList(List<String> fileBase64List, ThumbnailData thumbnailData) {
        List<ImageOption> imageOptions = new ArrayList<>();
        for (String base64 : fileBase64List) {
            ImageOption imageOption = imageToImageOption(base64, thumbnailData);
            imageOptions.add(imageOption);
        }
        return imageOptions;
    }

}
