package com.example.ThumbnailTester.controller;

import com.example.ThumbnailTester.Request.StatsRequest;
import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.dto.ImageOption;
import com.example.ThumbnailTester.dto.ThumbnailQueue;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import com.example.ThumbnailTester.services.ThumbnailQueueService;
import com.example.ThumbnailTester.services.ThumbnailTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.io.IOException;

@Controller
public class WebSocketController {
    private final SimpMessagingTemplate messagingTemplate;
    @Autowired
    private ThumbnailTestService thumbnailTestService;
    @Autowired
    private ThumbnailQueueService thumbnailQueueService;
    public WebSocketController(SimpMessagingTemplate messagingTemplate){
        this.messagingTemplate = messagingTemplate;
    }
    @MessageMapping("/thumbnail/test")
    public void handleTestMessage(@Payload ThumbnailRequest request) {
        try {
            for (ImageOption imageOption : request.getImageOptions()) {
                thumbnailQueueService.add(new ThumbnailQueueItem(request.getVideoUrl(), imageOption));
            }
            thumbnailTestService.runThumbnailTest(request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @MessageMapping("/thumbnail/stats/post")
    public void handleStatsMessage(@Payload StatsRequest statsRequest){
        System.out.println("Received stats request: " + statsRequest.toString());
    }

    @MessageMapping("/remove-thumbnail")
    public void handleRemoveThumbnail(@Payload Long thumbnailId, @Payload String googleId) {
        thumbnailQueueService.removeFromQueue(id);
    }
    @MessageMapping("/remove-text-thumbnail")
    public void handleRemoveThumbnail(@Payload Long thumbnailId, @Payload String googleId) {
        thumbnailQueueService.removeFromQueue(id);
    }
    @MessageMapping("/remove-thumbnail")
    public void handleRemoveThumbnail(@Payload Long thumbnailId, @Payload String googleId) {
        thumbnailQueueService.removeFromQueue(id);
    }
}
