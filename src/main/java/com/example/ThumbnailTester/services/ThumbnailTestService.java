package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.data.user.UserData;
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

    public ThumbnailTestService(SimpMessagingTemplate messagingTemplate, TaskScheduler taskScheduler) {
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
    }

    @Async
    public void runTest(ThumbnailRequest thumbnailRequest) throws IOException {
        UserData userData = userService.getByGoogleId(thumbnailRequest.getUserDTO().getGoogleId());
        List<String> files64 = thumbnailRequest.getFileBase64();
        //verifying that image is supported
        for(String base64:files64){
            if(!thumbnailService.isValid(base64)){
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "UnsupportedImage");
                return;
            }
        }
        // register user
        if(!userService.isExistById(userData.getId())){
            userService.save(userData);
        }




        long delayMillis = thumbnailRequest.getTestConfDTO().getTestingByTimeMinutes()*60*1000L;

    }
}
