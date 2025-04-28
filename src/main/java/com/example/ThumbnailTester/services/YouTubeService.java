package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.data.user.UserData;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ThumbnailSetResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoSnippet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Service

public class YouTubeService {
    @Value("${youtube.client.id}")
    private String clientId;

    @Value("${youtube.client.secret}")
    private String clientSecret;

    @Value("${application.name}")
    private String applicationName;

    public ThumbnailSetResponse uploadThumbnail(ThumbnailData thumbnailData, File thumbnailFile) throws IOException, GeneralSecurityException {
        Credential credential = buildCredentialFromRefreshToken(thumbnailData.getUser());
        YouTube youTube = new YouTube.Builder(
                credential.getTransport(),
                credential.getJsonFactory(),
                credential)
                .setApplicationName(applicationName)
                .build();
        FileContent mediaContent = new FileContent("image/jpeg", thumbnailFile);

        YouTube.Thumbnails.Set thumbnailSet = youTube.thumbnails()
                .set(getVideoIdFromUrl(thumbnailData.getVideoUrl()), mediaContent);

        return thumbnailSet.execute();
    }

    public void updateVideoTitle(UserData user, String videoId, String newTitle) throws IOException, GeneralSecurityException {
        Credential credential = buildCredentialFromRefreshToken(user);

        YouTube youtube = new YouTube.Builder(
                credential.getTransport(),
                credential.getJsonFactory(),
                credential)
                .setApplicationName(applicationName)
                .build();

        // Get video details
        YouTube.Videos.List videoRequest = youtube.videos().list("snippet");
        videoRequest.setId(videoId);
        VideoListResponse response = videoRequest.execute();

        if (!response.getItems().isEmpty()) {
            // get video
            Video video = response.getItems().get(0);
            VideoSnippet snippet = video.getSnippet();

            // edit title
            snippet.setTitle(newTitle);

            // update metadata
            video.setSnippet(snippet);

            // make request for update
            YouTube.Videos.Update videoUpdate = youtube.videos().update("snippet", video);
            videoUpdate.execute();
        } else {
            throw new IllegalArgumentException("Video with this id not found.");
        }
    }
    public String getVideoOwnerChannelId(UserData user, String videoId) throws IOException, GeneralSecurityException {
        Credential credential = buildCredentialFromRefreshToken(user);
        YouTube youtube = new YouTube.Builder(
                credential.getTransport(),
                credential.getJsonFactory(),
                credential)
                .setApplicationName(applicationName)
                .build();

        YouTube.Videos.List videoRequest = youtube.videos().list("snippet");
        videoRequest.setId(videoId);
        VideoListResponse response = videoRequest.execute();

        if (response.getItems().isEmpty()) {
            return null; // Просто вернуть null, если видео не найдено
        }
        return response.getItems().get(0).getSnippet().getChannelId();
    }

    public String getUserChannelId(UserData user) throws IOException, GeneralSecurityException {
        Credential credential = buildCredentialFromRefreshToken(user);

        YouTube youtube = new YouTube.Builder(
                credential.getTransport(),
                credential.getJsonFactory(),
                credential)
                .setApplicationName(applicationName)
                .build();

        YouTube.Channels.List channelRequest = youtube.channels().list("id");
        channelRequest.setMine(true); // Получить канал текущего пользователя
        var response = channelRequest.execute();

        if (response.getItems().isEmpty()) {
            return null; // Нет канала
        }
        return response.getItems().get(0).getId(); // Берём id первого канала
    }

    public Credential buildCredentialFromRefreshToken(UserData user) throws IOException, GeneralSecurityException {
        return new GoogleCredential.Builder()
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(JacksonFactory.getDefaultInstance())
                .setClientSecrets(clientId, clientSecret)
                .build()
                .setRefreshToken(user.getRefreshToken());
    }

    public String getVideoIdFromUrl(String videoUrl) {
        if (videoUrl.contains("v=")) {
            return videoUrl.split("v=")[1].split("&")[0];  // Avoid split issues with additional parameters
        } else {
            throw new IllegalArgumentException("Invalid YouTube URL format");
        }
    }
}