package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.Request.UserRequest;
import com.example.ThumbnailTester.data.thumbnail.TestConfType;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailTestConf;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ImageOption;
import com.example.ThumbnailTester.dto.ThumbnailQueue;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import com.example.ThumbnailTester.mapper.Mapper;
import com.example.ThumbnailTester.repositories.ThumbnailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing thumbnail tests.
 * This service handles the logic for running tests on YouTube thumbnails, including:
 * - Uploading thumbnails
 * - Updating video titles
 * - Collecting analytics data
 * - Sending progress and result notifications via WebSocket
 */
@Service
public class ThumbnailTestService {
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;

    @Autowired
    private ThumbnailService thumbnailService;
    @Autowired
    private UserService userService;
    @Autowired
    private YouTubeService youTubeService;
    @Autowired
    private YouTubeAnalyticsService youTubeAnalyticsService;
    @Autowired
    private ImageParser imageParser;
    @Autowired
    private Mapper mapper;
    @Autowired
    private ThumbnailRepository thumbnailRepository;

    @Autowired
    private ThumbnailQueueService thumbnailQueueService;

    /**
     * Constructor for ThumbnailTestService.
     *
     * @param messagingTemplate the messaging template for sending WebSocket messages
     * @param taskScheduler     the task scheduler for scheduling tasks
     */
    public ThumbnailTestService(SimpMessagingTemplate messagingTemplate, TaskScheduler taskScheduler) {
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
    }

    @Async
    public void runThumbnailTest(ThumbnailRequest thumbnailRequest) throws IOException {
        try {
            // 1. Retrieve user data
            UserRequest userRequest = thumbnailRequest.getUserDTO();
            UserData userData = userService.getByGoogleId(userRequest.getGoogleId());

            // 2. Map test configuration and thumbnail data
            ThumbnailTestConf testConf = mapper.testConfRequestToDTO(thumbnailRequest.getTestConfRequest());
            ThumbnailData thumbnailData = mapper.thumbnailRequestToData(thumbnailRequest);
            List<ImageOption> imageOptions = thumbnailRequest.getImageOptions();

            // 3. Validate image options
            if (imageOptions == null || imageOptions.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "NoImagesProvided");
                return;
            }

            for (ImageOption option : imageOptions) {
                if (!thumbnailService.isValid(option.getFileBase64())) {
                    messagingTemplate.convertAndSend("/topic/thumbnail/error", "UnsupportedImage");
                    return;
                }
            }

            // 4. Check video ownership
            String videoId = youTubeService.getVideoIdFromUrl(thumbnailRequest.getVideoUrl());
            String videoOwnerChannelId = youTubeService.getVideoOwnerChannelId(userData, videoId);
            String userChannelId = youTubeService.getUserChannelId(userData);

            if (videoOwnerChannelId == null) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "VideoNotFound");
                return;
            }

            if (userChannelId == null) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "UserChannelNotFound");
                return;
            }

            if (!userChannelId.equals(videoOwnerChannelId)) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "UnauthorizedVideoAccess");
                return;
            }

            // 5. Register user if not already registered
            if (!userService.isExistById(userData.getId())) {
                userData = new UserData(userRequest.getGoogleId(), userRequest.getRefreshToken());
            }

            // 6. Save thumbnail test data
            thumbnailService.save(thumbnailData);

            // 7. Start the test
            startTest(thumbnailRequest, testConf.getTestType(), thumbnailData);

            // 8. Schedule sending final test results
            long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;
            taskScheduler.schedule(() -> {
                try {
                    messagingTemplate.convertAndSend("/topic/thumbnail/result", getTestResults(thumbnailData));
                } catch (Exception e) {
                    messagingTemplate.convertAndSend("/topic/thumbnail/error", "ErrorSendingResults");
                }
            }, new java.util.Date(System.currentTimeMillis() + delayMillis));

        } catch (GeneralSecurityException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "GeneralSecurityException");
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "InternalServerError");
        }
    }


    private List<ImageOption> getTestResults(ThumbnailData thumbnailData) {
        return thumbnailData.getImageOptions();
    }

    @Async
    public void startTest(ThumbnailRequest thumbnailRequest, TestConfType testConfType, ThumbnailData thumbnailData) throws IOException {
        List<ImageOption> files64 = thumbnailRequest.getImageOptions();
        List<String> texts = thumbnailRequest.getTexts();
        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;

        // Initialize queue for the video URL
        ThumbnailQueue thumbnailQueue = thumbnailQueueService.getQueue(thumbnailRequest.getVideoUrl());

        // Add all image options to the queue
        for (ImageOption imageOption : thumbnailData.getImageOptions()) {
            thumbnailQueue.add(new ThumbnailQueueItem(thumbnailRequest.getVideoUrl(), imageOption));
        }

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        // Determine the number of tests to run based on the test configuration type
        int count = switch (testConfType) {
            case THUMBNAIL -> files64 != null ? files64.size() : 0;
            case TEXT -> texts != null ? texts.size() : 0;
            case THUMBNAILTEXT ->
                    (files64 != null && texts != null && files64.size() == texts.size()) ? files64.size() : 0;
        };

        if (count == 0) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "InvalidInputs");
            return;
        }

        ThumbnailQueueItem thumbnailQueueItem;
        // Process each test asynchronously
        while ((thumbnailQueueItem = thumbnailQueue.poll()) != null) {
            final ThumbnailQueueItem currentItem = thumbnailQueueItem;

            // Remove the current item from the queue
            thumbnailQueue.delete(currentItem);

            currentItem.setActive(true);
            chain = chain.thenComposeAsync(prev -> processSingleTest(
                    thumbnailData, currentItem, delayMillis, testConfType
            ));
        }

        // Notify when all tests are completed
        chain.thenRun(() -> messagingTemplate.convertAndSend("/topic/thumbnail/done", "Test for all images with videoUrl " + thumbnailRequest.getVideoUrl() + " completed"));
    }

    private CompletableFuture<Void> processSingleTest(
            ThumbnailData thumbnailData,
            ThumbnailQueueItem thumbnailQueueItem,
            long delayMillis,
            TestConfType testConfType
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                ImageOption imageOption = thumbnailQueueItem.getImageOption();
                String base64 = imageOption.getFileBase64();
                String text = imageOption.getText();

                // Update the thumbnail if required
                if (testConfType == TestConfType.THUMBNAIL || testConfType == TestConfType.THUMBNAILTEXT) {
                    File imageFile = imageParser.getFileFromBase64(base64);
                    youTubeService.uploadThumbnail(thumbnailData, imageFile);
                }

                // Update the video title if required
                if (testConfType == TestConfType.THUMBNAILTEXT || testConfType == TestConfType.TEXT) {
                    youTubeService.updateVideoTitle(thumbnailData.getUser(), thumbnailData.getVideoUrl(), text);
                }

                // Wait for the specified delay
                Thread.sleep(delayMillis);

                // Fetch and save statistics
                ThumbnailStats stats = youTubeAnalyticsService.getStats(thumbnailData.getUser(), thumbnailData, LocalDate.now());
                if (stats != null) {
                    imageOption.setThumbnailStats(stats);
                    stats.setImageOption(imageOption);
                    imageOption.setThumbnail(thumbnailData);

                    thumbnailService.save(thumbnailData);

                    // Send intermediate result via WebSocket
                    messagingTemplate.convertAndSend("/topic/thumbnail/progress", imageOption);
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error in processSingleTest: " + e.getMessage());
            }
        });
    }
}