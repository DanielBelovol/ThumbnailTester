package com.example.ThumbnailTester.controller;

import com.example.ThumbnailTester.Request.StatsRequest;
import com.example.ThumbnailTester.Request.ThumbnailRequest;
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
    public WebSocketController(SimpMessagingTemplate messagingTemplate){
        this.messagingTemplate = messagingTemplate;
    }
    @MessageMapping("/thumbnail/test")
    public void handleTestMessage(@Payload ThumbnailRequest request) {
        try {
            thumbnailTestService.runThumbnailTest(request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @MessageMapping("/thumbnail/stats/post")
    public void handleStatsMessage(@Payload StatsRequest statsRequest){

    }
}
