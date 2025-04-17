package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.Request.UserRequest;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailTestConf;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.mapper.Mapper;
import com.example.ThumbnailTester.result.ThumbnailResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class ThumbnailTestService {
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;
    @Autowired
    private ThumbnailService thumbnailService;
    @Autowired
    private UserService userService;
    @Autowired
    private ThumbnailStatsService thumbnailStatsService;
    @Autowired
    private YouTubeService youTubeService;
    @Autowired
    private YouTubeAnalyticsService youTubeAnalyticsService;
    @Autowired
    private Mapper mapper;

    public ThumbnailTestService(SimpMessagingTemplate messagingTemplate, TaskScheduler taskScheduler) {
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
    }

    @Async
    public void runTest(ThumbnailRequest thumbnailRequest) throws IOException {
        UserRequest userRequest = thumbnailRequest.getUserDTO();
        UserData userData = userService.getByGoogleId(userRequest.getGoogleId());
        ThumbnailTestConf testConf = mapper.testConfRequestToDTO(thumbnailRequest.getTestConfRequest());
        ThumbnailData thumbnailData = mapper.thumbnailRequestToData(thumbnailRequest);
        List<String> files64 = thumbnailRequest.getFileBase64();
        if (files64 == null || files64.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "NoImagesProvided");
            return;
        }
        //verifying that image is supported
        for(String base64:files64){
            if(!thumbnailService.isValid(base64)){
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "UnsupportedImage");
                return;
            }
        }
        // register user
        if(!userService.isExistById(userData.getId())){
            userData = new UserData(userRequest.getGoogleId(), userRequest.getRefreshToken());
        }


        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes()*60*1000L;
        UserData finalUserData = userData;
        taskScheduler.schedule(() -> {
            // 💡 Этот код выполнится только ПОСЛЕ таймера






            // например, тут можно собрать статистику (views, CTR)
            ThumbnailResult result = new ThumbnailResult();
            result.setImageResultList(null);
            result.setVideoUrl(thumbnailRequest.getVideoUrl());
            result.setTestConf(testConf);
            result.setUser(finalUserData);
            // 🔥 Отправляем по WebSocket
            messagingTemplate.convertAndSend("/topic/thumbnail/result", result);
        }, new java.util.Date(System.currentTimeMillis() + delayMillis));
    }
    public void startTest(ThumbnailRequest thumbnailRequest) {

    }
}
