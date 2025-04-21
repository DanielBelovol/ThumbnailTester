package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.Request.UserRequest;
import com.example.ThumbnailTester.data.thumbnail.*;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ImageOption;
import com.example.ThumbnailTester.mapper.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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

    /**
     * Runs a thumbnail test based on the provided request.
     * This method validates inputs, saves thumbnail data, and starts the test process.
     *
     * @param thumbnailRequest the request containing test configuration and image options
     * @throws IOException if an error occurs during file processing
     */
    @Async
    public void runThumbnailTest(ThumbnailRequest thumbnailRequest) throws IOException {
        // Retrieve user data from the request
        UserRequest userRequest = thumbnailRequest.getUserDTO();
        UserData userData = userService.getByGoogleId(userRequest.getGoogleId());

        // Map test configuration and thumbnail data
        ThumbnailTestConf testConf = mapper.testConfRequestToDTO(thumbnailRequest.getTestConfRequest());
        ThumbnailData thumbnailData = mapper.thumbnailRequestToData(thumbnailRequest);
        List<ImageOption> imageOptions = thumbnailRequest.getImageOptions();

        // Validate image options
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

        // Register user if not already registered
        if (!userService.isExistById(userData.getId())) {
            userData = new UserData(userRequest.getGoogleId(), userRequest.getRefreshToken());
        }

        // Save thumbnail data
        thumbnailService.save(thumbnailData);

        // Start the test process
        startTest(thumbnailRequest, testConf.getTestType(), thumbnailData);

        // Schedule sending of final results after the test duration
        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;
        taskScheduler.schedule(() -> {
            try {
                messagingTemplate.convertAndSend("/topic/thumbnail/result", getTestResults(thumbnailData));
            } catch (Exception e) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "ErrorSendingResults");
            }
        }, new java.util.Date(System.currentTimeMillis() + delayMillis));
    }

    /**
     * Retrieves the test results for the given thumbnail data.
     *
     * @param thumbnailData the thumbnail data containing image options
     * @return a list of image options representing the test results
     */
    private List<ImageOption> getTestResults(ThumbnailData thumbnailData) {
        return thumbnailData.getImageOptions();
    }

    /**
     * Starts the test process for the given thumbnail request.
     * This method processes each image or text option asynchronously.
     *
     * @param thumbnailRequest the request containing test configuration and image options
     * @param testConfType     the type of test to run (e.g., THUMBNAIL, TEXT, THUMBNAILTEXT)
     * @param thumbnailData    the thumbnail data to be tested
     * @throws IOException if an error occurs during file processing
     */
    @Async
    public void startTest(ThumbnailRequest thumbnailRequest, TestConfType testConfType, ThumbnailData thumbnailData) throws IOException {
        List<ImageOption> files64 = thumbnailRequest.getImageOptions();
        List<String> texts = thumbnailRequest.getTexts();
        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;

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

        // Process each test asynchronously
        for (int i = 0; i < count; i++) {
            final int index = i;
            final String base64 = (files64 != null && i < files64.size()) ? files64.get(i).getFileBase64() : null;
            final String text = (texts != null && i < texts.size()) ? texts.get(i) : null;

            chain = chain.thenComposeAsync(prev -> processSingleTest(thumbnailData, base64, text, index, delayMillis, testConfType));
        }

        // Notify when all tests are completed
        chain.thenRun(() -> messagingTemplate.convertAndSend("/topic/thumbnail/done", "Test for all images with videoUrl " + thumbnailRequest.getVideoUrl() + " completed"));
    }

    /**
     * Processes a single test for a thumbnail or text.
     * This method updates the thumbnail or video title, fetches analytics data, and sends progress notifications.
     *
     * @param thumbnailData the thumbnail data to be tested
     * @param base64        the base64-encoded image data
     * @param text          the text to update the video title with
     * @param index         the index of the current test
     * @param delayMillis   the delay between tests in milliseconds
     * @param testConfType  the type of test to run (e.g., THUMBNAIL, TEXT, THUMBNAILTEXT)
     * @return a CompletableFuture representing the asynchronous operation
     */
    private CompletableFuture<Void> processSingleTest(
            ThumbnailData thumbnailData,
            String base64,
            String text,
            int index,
            long delayMillis,
            TestConfType testConfType
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
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
                ThumbnailStats stats = youTubeAnalyticsService.getStats(thumbnailData.getUser(), LocalDate.now());
                if (stats != null) {
                    ImageOption imageOption = thumbnailData.getImageOptions().get(index);

                    if (text != null) {
                        imageOption.setText(text);
                    }

                    imageOption.setThumbnailStats(stats);
                    stats.setImageOption(imageOption);
                    imageOption.setThumbnail(thumbnailData);

                    // Save the updated thumbnail data
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