package com.example.ThumbnailTester.controller;

import com.example.ThumbnailTester.services.GoogleOauthService;
import com.example.ThumbnailTester.services.UserService;
import com.example.ThumbnailTester.services.YouTubeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("")
public class ServiceController {
    private final UserService userService;
    private final YouTubeService youTubeService;
    private final GoogleOauthService googleOauthService;

    @Autowired
    public ServiceController(UserService userService, YouTubeService youTubeService, GoogleOauthService googleOauthService) {
        this.userService = userService;
        this.youTubeService = youTubeService;
        this.googleOauthService = googleOauthService;
    }
}
