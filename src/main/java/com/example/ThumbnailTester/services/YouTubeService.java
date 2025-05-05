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


    public void updateVideoTitle(UserData user, String videoId, String newTitle) {
        try {
            log.info("update video title");
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
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "Video with id " + videoId + " not found.");
            }
        } catch (IOException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Error updating video title: " + e.getMessage());
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
            // Refresh the token to ensure it's valid
            credential.refreshToken();

            return credential;

        } catch (IOException | GeneralSecurityException e) {
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