package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.Request.UserRequest;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailTestConf;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ImageOption;
import com.example.ThumbnailTester.mapper.Mapper;
import com.example.ThumbnailTester.result.ThumbnailResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    public void runThumbnailTest(ThumbnailRequest thumbnailRequest) throws IOException {
        UserRequest userRequest = thumbnailRequest.getUserDTO();
        UserData userData = userService.getByGoogleId(userRequest.getGoogleId());
        ThumbnailTestConf testConf = mapper.testConfRequestToDTO(thumbnailRequest.getTestConfRequest());
        ThumbnailData thumbnailData = mapper.thumbnailRequestToData(thumbnailRequest);
        List<ImageOption> imageOptions = thumbnailRequest.getImageOptions();
        if (imageOptions == null || imageOptions.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "NoImagesProvided");
            return;
        }
        //verifying that image is supported
        for(ImageOption option:imageOptions){
            if(!thumbnailService.isValid(option.getFileBase64())){
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "UnsupportedImage");
                return;
            }
        }
        // register user
        if(!userService.isExistById(userData.getId())){
            userData = new UserData(userRequest.getGoogleId(), userRequest.getRefreshToken());
        }
        thumbnailService.save(thumbnailData);


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
    @Async
    public void startTest(ThumbnailRequest thumbnailRequest) {

    }

    private CompletableFuture<Void> processSingleImage(String base64, int index, long delayMillis) {
        return CompletableFuture.runAsync(() -> {
            try {

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
