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

import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Slf4j
@Service
public class YouTubeService {
    @Value("${youtube.client.id}")
    private String clientId;

    @Value("${youtube.client.secret}")
    private String clientSecret;

    @Value("${application.name}")
    private String applicationName;
    @Autowired
    SupaBaseImageService supaBaseImageService;
    private final SimpMessagingTemplate messagingTemplate;

    public YouTubeService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void uploadThumbnail(ThumbnailData thumbnailData, File thumbnailFile) {
        try {
            log.info("Uploading thumbnail started");
            Credential credential = buildCredentialFromRefreshToken(thumbnailData.getUser());
            log.info("Refresh token: " + credential.getRefreshToken());
            YouTube youTube = new YouTube.Builder(
                    credential.getTransport(),
                    credential.getJsonFactory(),
                    credential)
                    .setApplicationName(applicationName)
                    .build();

            // Prepare the thumbnail file for upload
            FileContent mediaContent = new FileContent("image/jpeg", thumbnailFile);
            log.info("file content: " + mediaContent);

            // Execute the thumbnail upload
            YouTube.Thumbnails.Set thumbnailSet = youTube.thumbnails()
                    .set(getVideoIdFromUrl(thumbnailData.getVideoUrl()), mediaContent);
            log.info("thumbnail set: " + thumbnailSet);

            ThumbnailSetResponse response = thumbnailSet.execute();
            log.info("thumbnail response: " + response);

            // Check if the response contains the expected data (successful upload)
            if (response != null && response.getItems() != null && !response.getItems().isEmpty()) {
                messagingTemplate.convertAndSend("/topic/thumbnail/success", "Thumbnail uploaded successfully.");
                supaBaseImageService.deleteFileWithPath(thumbnailFile);
            } else {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "Thumbnail upload failed: No response items found.");
            }
        } catch (IOException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Failed to upload thumbnail: " + e.getMessage());
        }
    }

    private YouTube buildYouTubeService(UserData user) throws Exception {
        UserCredentials userCredentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(user.getRefreshToken())
                .build();

        HttpCredentialsAdapter credentialsAdapter = new HttpCredentialsAdapter(userCredentials);

        return new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                credentialsAdapter)
                .setApplicationName(applicationName)
                .build();
    }


    public void updateVideoTitle(UserData user, String videoId, String newTitle) {
        try {
            log.info("Updating video title. videoId={}, newTitle={}", videoId, newTitle);
            Credential credential = buildCredentialFromRefreshToken(user);
            if (credential == null) {
                log.error("Credential is null, cannot update video title");
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "Invalid credentials for updating video title.");
                return;
            }

            YouTube youtube = new YouTube.Builder(
                    credential.getTransport(),
                    credential.getJsonFactory(),
                    credential)
                    .setApplicationName(applicationName)
                    .build();

            // Получаем детали видео
            YouTube.Videos.List videoRequest = youtube.videos().list("snippet");
            videoRequest.setId(videoId);
            VideoListResponse response = videoRequest.execute();

            if (response.getItems().isEmpty()) {
                String errMsg = "Video with id " + videoId + " not found.";
                log.error(errMsg);
                messagingTemplate.convertAndSend("/topic/thumbnail/error", errMsg);
                return;
            }

            Video video = response.getItems().get(0);
            VideoSnippet snippet = video.getSnippet();

            // Логируем текущий заголовок
            log.info("Current video title: {}", snippet.getTitle());

            // Обновляем заголовок
            snippet.setTitle(newTitle);
            video.setSnippet(snippet);

            // Выполняем обновление
            YouTube.Videos.Update videoUpdate = youtube.videos().update("snippet", video);
            Video updatedVideo = videoUpdate.execute();

            // Логируем результат
            log.info("Video title updated successfully to: {}", updatedVideo.getSnippet().getTitle());
            messagingTemplate.convertAndSend("/topic/thumbnail/success", "Video title updated successfully.");

        } catch (IOException e) {
            log.error("IOException while updating video title", e);
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Error updating video title: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while updating video title", e);
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Unexpected error updating video title: " + e.getMessage());
        }
    }


    public String getVideoOwnerChannelId(UserData user, String videoId) {
        try {
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
                return null;
            }
            return response.getItems().get(0).getSnippet().getChannelId();
        } catch (IOException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Error getting video owner channel ID: " + e.getMessage());
        }
        return null;
    }

    public String getUserChannelId(UserData user) {
        try {
            Credential credential = buildCredentialFromRefreshToken(user);

            YouTube youtube = new YouTube.Builder(
                    credential.getTransport(),
                    credential.getJsonFactory(),
                    credential)
                    .setApplicationName(applicationName)
                    .build();

            YouTube.Channels.List channelRequest = youtube.channels().list("id");
            channelRequest.setMine(true);
            var response = channelRequest.execute();

            if (response.getItems().isEmpty()) {
                return null;
            }
            return response.getItems().get(0).getId();
        } catch (IOException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Error getting user channel ID: " + e.getMessage());
        }
        return null;
    }

    public Credential buildCredentialFromRefreshToken(UserData user) {
        try {
            Credential credential = new GoogleCredential.Builder()
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(JacksonFactory.getDefaultInstance())
                    .setClientSecrets(clientId, clientSecret)
                    .build()
                    .setRefreshToken(user.getRefreshToken());

            // Попытка обновить токен
            boolean refreshed = credential.refreshToken();
            if (refreshed) {
                log.info("Successfully refreshed access token for user with GoogleId: {}", user.getGoogleId());
                log.debug("New access token: {}", credential.getAccessToken());
            } else {
                log.warn("Failed to refresh access token for user with GoogleId: {}", user.getGoogleId());
            }

            return credential;

        } catch (IOException | GeneralSecurityException e) {
            log.error("Error building YouTube credential for user with GoogleId: {}", user.getGoogleId(), e);
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Error building YouTube service: " + e.getMessage());
        }
        return null;
    }


    public String getVideoIdFromUrl(String videoUrl) {
        if (videoUrl.contains("v=")) {
            return videoUrl.split("v=")[1].split("&")[0];  // Avoid split issues with additional parameters
        } else {
            // If URL format is invalid, send an error message using messagingTemplate
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Invalid YouTube URL format: " + videoUrl);
            return null;  // Or you can return an empty string or another value to signify an error
        }
    }
}