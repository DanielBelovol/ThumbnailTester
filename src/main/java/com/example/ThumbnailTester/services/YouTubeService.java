package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.util.AESUtil;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;

@Slf4j
@Service
public class YouTubeService {
    private static final String IMAGE_MIME_TYPE = "image/jpeg";
    private static final String SNIPPET_PART = "snippet";
    private static final String TOPIC_SUCCESS = "/topic/thumbnail/success";
    private static final String TOPIC_ERROR = "/topic/thumbnail/error";
    private static final String ERR_INVALID_CREDENTIALS_UPDATE_TITLE = "Invalid credentials for updating video title.";
    private static final String ERR_VIDEO_NOT_FOUND = "Video with id %s not found.";
    private static final String ERR_ERROR_UPDATING_TITLE = "Error updating video title: %s";
    private static final String ERR_UNEXPECTED_ERROR_UPDATING_TITLE = "Unexpected error updating video title: %s";
    private static final String ERR_INVALID_CREDENTIALS_GET_CHANNEL_ID = "Invalid credentials for getting user channel ID.";
    private static final String ERR_ERROR_GETTING_CHANNEL_ID = "Error getting user channel ID: %s";
    private static final String ERR_INVALID_CREDENTIALS_GET_OWNER_CHANNEL_ID = "Invalid credentials for getting video owner channel ID.";
    private static final String ERR_ERROR_GETTING_OWNER_CHANNEL_ID = "Error getting video owner channel ID: %s";
    private static final String ERR_ERROR_BUILDING_CREDENTIAL = "Error building YouTube service: %s";
    private static final String ERR_VIDEO_URL_EMPTY = "Video URL is empty or null";
    private static final String ERR_INVALID_YOUTUBE_URL_FORMAT = "Invalid YouTube URL format: %s";
    private static final String ERR_INVALID_CREDENTIALS_GET_TITLE = "Invalid credentials for getting video title.";
    private static final String ERR_ERROR_GETTING_TITLE = "Error getting video title: %s";
    private static final String ERR_UNEXPECTED_ERROR_GETTING_TITLE = "Unexpected error getting video title: %s";

    @Value("${youtube.client.id}")
    private String clientId;

    @Value("${youtube.client.secret}")
    private String clientSecret;

    @Value("${application.name}")
    private String applicationName;

    @Autowired
    private SupaBaseImageService supaBaseImageService;
    @Autowired
    private AESUtil aesUtil;

    private final SimpMessagingTemplate messagingTemplate;

    public YouTubeService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Uploads a thumbnail image to YouTube.
     *
     * @param thumbnailData data of the thumbnail
     * @param thumbnailFile file containing the thumbnail image
     * @throws IOException if upload fails
     */
    public void uploadThumbnail(ThumbnailData thumbnailData, File thumbnailFile) throws IOException {
        log.info("Uploading thumbnail started");
        Credential credential = buildCredentialFromRefreshToken(thumbnailData.getUser());
        if (credential == null) {
            throw new IOException("Failed to build credentials for user");
        }

        YouTube youTube = buildYouTubeClient(credential);

        FileContent mediaContent = new FileContent(IMAGE_MIME_TYPE, thumbnailFile);
        log.debug("File content prepared for upload: {}", mediaContent);

        YouTube.Thumbnails.Set thumbnailSet = youTube.thumbnails()
                .set(getVideoIdFromUrl(thumbnailData.getVideoUrl()), mediaContent);

        try {
            ThumbnailSetResponse response = thumbnailSet.execute();
            log.debug("Thumbnail upload response: {}", response);

            if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
                throw new IOException("Thumbnail upload failed: No response items found.");
            }

            messagingTemplate.convertAndSend(TOPIC_SUCCESS, "Thumbnail uploaded successfully.");
            supaBaseImageService.deleteFileWithPath(thumbnailFile);
        }catch (GoogleJsonResponseException e){
            if (e.getStatusCode() == 429) {
                throw e;
            } else {
                throw new IOException();
            }
        }
    }

    /**
     * Updates the title of a YouTube video.
     *
     * @param user     the user performing the update
     * @param videoId  the ID of the video to update
     * @param newTitle the new title to set
     */
    public void updateVideoTitle(UserData user, String videoId, String newTitle) {
        try {
            log.info("Updating video title. videoId={}, newTitle={}", videoId, newTitle);
            Credential credential = buildCredentialFromRefreshToken(user);
            if (credential == null) {
                sendError(ERR_INVALID_CREDENTIALS_UPDATE_TITLE);
                return;
            }

            YouTube youtube = buildYouTubeClient(credential);

            Optional<Video> videoOpt = getVideoById(youtube, videoId);
            if (videoOpt.isEmpty()) {
                sendError(String.format(ERR_VIDEO_NOT_FOUND, videoId));
                return;
            }

            Video video = videoOpt.get();
            VideoSnippet snippet = video.getSnippet();
            log.info("Current video title: {}", snippet.getTitle());

            snippet.setTitle(newTitle);
            video.setSnippet(snippet);

            Video updatedVideo = youtube.videos().update(SNIPPET_PART, video).execute();
            log.info("Video title updated successfully to: {}", updatedVideo.getSnippet().getTitle());
            messagingTemplate.convertAndSend(TOPIC_SUCCESS, "Video title updated successfully.");

        } catch (IOException e) {
            log.error("IOException while updating video title", e);
            sendError(String.format(ERR_ERROR_UPDATING_TITLE, e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error while updating video title", e);
            sendError(String.format(ERR_UNEXPECTED_ERROR_UPDATING_TITLE, e.getMessage()));
        }
    }

    /**
     * Retrieves the channel ID of the owner of a video.
     *
     * @param user    the user requesting the info
     * @param videoId the ID of the video
     * @return the channel ID of the video owner or null if not found
     */
    public String getVideoOwnerChannelId(UserData user, String videoId) {
        try {
            Credential credential = buildCredentialFromRefreshToken(user);
            if (credential == null) {
                sendError(ERR_INVALID_CREDENTIALS_GET_OWNER_CHANNEL_ID);
                return null;
            }

            YouTube youtube = buildYouTubeClient(credential);

            Optional<Video> videoOpt = getVideoById(youtube, videoId);
            return videoOpt.map(v -> v.getSnippet().getChannelId()).orElse(null);

        } catch (IOException e) {
            log.error("Error getting video owner channel ID", e);
            sendError(String.format(ERR_ERROR_GETTING_OWNER_CHANNEL_ID, e.getMessage()));
            return null;
        }
    }

    /**
     * Retrieves the channel ID of the authenticated user.
     *
     * @param user the user
     * @return the channel ID or null if not found
     */
    public String getUserChannelId(UserData user) {
        try {
            Credential credential = buildCredentialFromRefreshToken(user);
            if (credential == null) {
                sendError(ERR_INVALID_CREDENTIALS_GET_CHANNEL_ID);
                return null;
            }

            YouTube youtube = buildYouTubeClient(credential);

            YouTube.Channels.List channelRequest = youtube.channels().list("id");
            channelRequest.setMine(true);
            ChannelListResponse response = channelRequest.execute();

            if (response.getItems().isEmpty()) {
                return null;
            }
            return response.getItems().get(0).getId();

        } catch (IOException e) {
            log.error("Error getting user channel ID", e);
            sendError(String.format(ERR_ERROR_GETTING_CHANNEL_ID, e.getMessage()));
            return null;
        }
    }

    /**
     * Builds a Credential object from the user's refresh token.
     *
     * @param user the user
     * @return Credential or null if failed
     */
    public Credential buildCredentialFromRefreshToken(UserData user) {
        try {
            Credential credential = new GoogleCredential.Builder()
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(JacksonFactory.getDefaultInstance())
                    .setClientSecrets(clientId, clientSecret)
                    .build()
                    .setRefreshToken(aesUtil.decrypt(user.getRefreshToken()));

            boolean refreshed = credential.refreshToken();
            if (refreshed) {
                log.info("Successfully refreshed access token for user with GoogleId: {}", user.getGoogleId());
                log.debug("New access token: {}", credential.getAccessToken().substring(0,10));
            } else {
                log.warn("Failed to refresh access token for user with GoogleId: {}", user.getGoogleId());
            }

            return credential;

        } catch (IOException | GeneralSecurityException e) {
            log.error("Error building YouTube credential for user with GoogleId: {}", user.getGoogleId(), e);
            sendError(String.format(ERR_ERROR_BUILDING_CREDENTIAL, e.getMessage()));
            return null;
        } catch (Exception e) {
            log.error("Error decoding refresh token");
            throw new RuntimeException(e);
        }
    }

    /**
     * Extracts the video ID from a YouTube video URL.
     *
     * @param videoUrl the full YouTube video URL
     * @return the video ID or null if the URL format is invalid
     */
    public String getVideoIdFromUrl(String videoUrl) {
        if (isBlank(videoUrl)) {
            sendError(ERR_VIDEO_URL_EMPTY);
            return null;
        }

        int vIndex = videoUrl.indexOf("v=");
        if (vIndex < 0) {
            sendError(String.format(ERR_INVALID_YOUTUBE_URL_FORMAT, videoUrl));
            return null;
        }

        String idPart = videoUrl.substring(vIndex + 2);
        int ampIndex = idPart.indexOf('&');
        return ampIndex > 0 ? idPart.substring(0, ampIndex) : idPart;
    }

    /**
     * Retrieves the title of a YouTube video.
     *
     * @param user    the user requesting the title
     * @param videoId the ID of the video
     * @return the video title or null if not found
     */
    public String getVideoTitle(UserData user, String videoId) {
        try {
            Credential credential = buildCredentialFromRefreshToken(user);
            if (credential == null) {
                sendError(ERR_INVALID_CREDENTIALS_GET_TITLE);
                return null;
            }

            YouTube youtube = buildYouTubeClient(credential);

            Optional<Video> videoOpt = getVideoById(youtube, videoId);
            if (videoOpt.isEmpty()) {
                sendError(String.format(ERR_VIDEO_NOT_FOUND, videoId));
                return null;
            }

            return videoOpt.get().getSnippet().getTitle();

        } catch (IOException e) {
            log.error("IOException while getting video title", e);
            sendError(String.format(ERR_ERROR_GETTING_TITLE, e.getMessage()));
            return null;
        } catch (Exception e) {
            log.error("Unexpected error while getting video title", e);
            sendError(String.format(ERR_UNEXPECTED_ERROR_GETTING_TITLE, e.getMessage()));
            return null;
        }
    }

// --- Private helper methods ---

    private YouTube buildYouTubeClient(Credential credential) {
        return new YouTube.Builder(
                credential.getTransport(),
                credential.getJsonFactory(),
                credential)
                .setApplicationName(applicationName)
                .build();
    }

    private Optional<Video> getVideoById(YouTube youtube, String videoId) throws IOException {
        if (isBlank(videoId)) {
            return Optional.empty();
        }
        YouTube.Videos.List videoRequest = youtube.videos().list(SNIPPET_PART);
        videoRequest.setId(videoId);
        VideoListResponse response = videoRequest.execute();

        if (response.getItems().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(response.getItems().get(0));
    }

    private void sendError(String message) {
        log.error(message);
        messagingTemplate.convertAndSend(TOPIC_ERROR, message);
    }

    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}