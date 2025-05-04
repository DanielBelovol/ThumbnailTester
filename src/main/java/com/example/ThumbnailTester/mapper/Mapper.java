package com.example.ThumbnailTester.mapper;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.Request.ThumbnailTestConfRequest;
import com.example.ThumbnailTester.data.thumbnail.*;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ImageOption;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import com.example.ThumbnailTester.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class Mapper {
    @Autowired
    private UserService userService;
    private static final Logger log = LoggerFactory.getLogger(Mapper.class);

    public ThumbnailTestConf testConfRequestToDTO(ThumbnailTestConfRequest request) {
        log.info("Entering testConfRequestToDTO method.");
        log.info("TestType: " + request.getTestType());
        log.info("TestingType: " + request.getTestingType());
        log.info("CriterionOfWinner: " + request.getCriterionOfWinner());

        ThumbnailTestConf testConf = new ThumbnailTestConf();

        try {
            testConf.setTestType(TestConfType.valueOf(request.getTestType()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid TestConfType: " + request.getTestType(), e);// Или можно вернуть null или задать значение по умолчанию
        }

        try {
            testConf.setTestingType(TestingType.valueOf(request.getTestingType()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid TestingType: " + request.getTestingType(), e);
        }

        testConf.setTestingByTimeMinutes(request.getTestingByTimeMinutes());
        testConf.setTestingByMetrics(request.getTestingByMetrics());

        try {
            testConf.setCriterionOfWinner(CriterionOfWinner.valueOf(request.getCriterionOfWinner()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid CriterionOfWinner: " + request.getCriterionOfWinner(), e);
            throw e;
        }

        return testConf;
    }

    public ThumbnailData thumbnailRequestToData(ThumbnailRequest thumbnailRequest) {
        log.info("Entering thumbnailRequestToData method.");
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

        if (thumbnailData == null) {
            log.error("ThumbnailData is null when attempting to create image options");
        } else {
            log.info("ThumbnailData is not null, proceeding with image option creation");
        }

        log.info("Starting foreach method.");
        for (String base64 : fileBase64List) {
            ImageOption imageOption = imageToImageOption(base64, thumbnailData);
            imageOptions.add(imageOption);
        }
        log.info("ended foreach method.");
        return imageOptions;
    }

}
