package com.example.ThumbnailTester.controller;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {
    private final SimpMessagingTemplate messagingTemplate;
    public WebSocketController(SimpMessagingTemplate messagingTemplate){
        this.messagingTemplate = messagingTemplate;
    }
    @MessageMapping("/thumbnail/create")
    public void handleMessage(@Payload ThumbnailRequest request) {

    }
}
