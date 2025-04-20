package com.example.ThumbnailTester.mapper;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.Request.ThumbnailTestConfRequest;
import com.example.ThumbnailTester.data.thumbnail.*;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ImageOption;
import com.example.ThumbnailTester.services.ImageParser;
import com.example.ThumbnailTester.services.UserService;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Mapper {
    @Autowired
    private UserService userService;

    public ThumbnailTestConf testConfRequestToDTO(ThumbnailTestConfRequest request){
        ThumbnailTestConf testConf = new ThumbnailTestConf();

        testConf.setTestType(TestConfType.valueOf(request.getTestType()));
        testConf.setTestingType(TestingType.valueOf(request.getTestingType()));
        testConf.setTestingByTimeMinutes(request.getTestingByTimeMinutes());
        testConf.setTestingByMetrics(request.getTestingByMetrics());
        testConf.setCriterionOfWinner(CriterionOfWinner.valueOf(request.getCriterionOfWinner()));

        return testConf;
    }
    public ThumbnailData thumbnailRequestToData(ThumbnailRequest thumbnailRequest){
        UserData userData = userService.getByGoogleId(thumbnailRequest.getUserDTO().getGoogleId());


        ThumbnailData thumbnailData = new ThumbnailData();
        thumbnailData.setImageOptions(thumbnailRequest.getImageOptions());
        thumbnailData.setVideoUrl(thumbnailRequest.getVideoUrl());
        thumbnailData.setTestConf(testConfRequestToDTO(thumbnailRequest.getTestConfRequest()));
        if(userService.isExistById(userData.getId())){
            thumbnailData.setUser(userData);
        }else{
            thumbnailData.setUser(new UserData(thumbnailRequest.getUserDTO().getGoogleId(), thumbnailRequest.getUserDTO().getRefreshToken()));
        }
        return thumbnailData;
    }
    public ImageOption thumbnailDataToImageOption(ThumbnailData thumbnailData){
        ImageOption imageOption = new ImageOption();
//        imageOption.set
        return imageOption;
    }
}
