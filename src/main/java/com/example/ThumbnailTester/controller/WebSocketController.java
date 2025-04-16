package com.example.ThumbnailTester.controller;

import com.example.ThumbnailTester.DTO.ThumbnailDTO;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {
    @MessageMapping("/socket")
    public void handleMessage(@Payload ThumbnailDTO dto) {

    }
}
