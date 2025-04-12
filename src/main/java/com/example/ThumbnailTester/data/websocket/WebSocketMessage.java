package com.example.ThumbnailTester.data.websocket;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailTestConf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    private String type; //test, getResult
    private String googleId;
    private List<String> images;
    private ThumbnailTestConf testConf;
}
