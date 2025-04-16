package com.example.ThumbnailTester.controller;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {
    @MessageMapping("/thumbnail/create")
    public void handleMessage(@Payload ThumbnailRequest request) {

    }
}
