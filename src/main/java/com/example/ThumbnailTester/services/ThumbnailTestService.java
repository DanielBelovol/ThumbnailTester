package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.data.thumbnail.*;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ImageOption;
import com.example.ThumbnailTester.dto.ThumbnailQueue;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for managing thumbnail tests.
 * Handles logic for running tests on YouTube thumbnails, including:
 * - Uploading thumbnails
 * - Updating video titles
 * - Collecting analytics data
 * - Sending progress and result notifications via WebSocket
 */
@Service
public class ThumbnailTestService {
    private static final Logger log = LoggerFactory.getLogger(ThumbnailTestService.class);

    // Constants for timing (in milliseconds)
    private static final long DEFAULT_TITLE_UPDATE_TIMEOUT_MILLIS = 100_000L;
    private static final long DEFAULT_TITLE_UPDATE_POLL_INTERVAL_MILLIS = 5_000L;
    private static final long THUMBNAIL_UPLOAD_WAIT_MILLIS = 10_000L;

    // WebSocket topics
    private static final String TOPIC_ERROR = "/topic/thumbnail/error";
    private static final String TOPIC_PROGRESS = "/topic/thumbnail/progress";
    private static final String TOPIC_FINAL = "/topic/thumbnail/final";
    private static final String TOPIC_RESULT = "/topic/thumbnail/result";

    // Error messages
    private static final String ERR_NO_IMAGES_PROVIDED = "NoImagesProvided";
    private static final String ERR_INVALID_VIDEO_URL = "InvalidVideoUrl";
    private static final String ERR_VIDEO_NOT_FOUND = "VideoNotFound";
    private static final String ERR_USER_CHANNEL_NOT_FOUND = "UserChannelNotFound";
    private static final String ERR_UNAUTHORIZED_VIDEO_ACCESS = "UnauthorizedVideoAccess";
    private static final String ERR_INTERNAL_SERVER = "InternalServerError";
    private static final String ERR_ERROR_SENDING_RESULTS = "ErrorSendingResults";
    private static final String ERR_INVALID_INPUTS = "InvalidInputs";
    private static final String ERR_THUMBNAIL_UPLOAD_FAILED = "Thumbnail upload failed";
    private static final String ERR_ERROR_UPDATING_TITLE = "Error with updating title";
    private static final String ERR_FINAL_RESULT_ERROR = "FinalResultError";

    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;

    @Autowired
    private ThumbnailService thumbnailService;

    @Autowired
    private SupaBaseImageService supaBaseImageService;

    @Autowired
    private YouTubeService youTubeService;

    @Autowired
    private UserService userService;

    @Autowired
    private YouTubeAnalyticsService youTubeAnalyticsService;

    @Autowired
    private ThumbnailQueueService thumbnailQueueService;

    public ThumbnailTestService(SimpMessagingTemplate messagingTemplate, TaskScheduler taskScheduler) {
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
    }

    @Async
    public void runThumbnailTest(ThumbnailRequest thumbnailRequest, ThumbnailData thumbnailData) {
        try {
            UserData userData = thumbnailData.getUser();
            log.info("Starting thumbnail test for user: {}", userData);

            ThumbnailTestConf testConf = thumbnailData.getTestConf();
            List<ImageOption> imageOptions = thumbnailData.getImageOptions();

            if (imageOptions == null || imageOptions.isEmpty()) {
                sendError(ERR_NO_IMAGES_PROVIDED);
                return;
            }

            if (!validateImageOptions(imageOptions)) {
                return;
            }

            String videoId = youTubeService.getVideoIdFromUrl(thumbnailData.getVideoUrl());
            if (videoId == null) {
                sendError(ERR_INVALID_VIDEO_URL);
                return;
            }

            if (!validateVideoOwnership(userData, videoId)) {
                return; // Errors sent inside method
            }

            if (userData.getId() == null) {
                userData = userService.save(userData);
                thumbnailData.setUser(userData);
            }

            thumbnailData = thumbnailService.save(thumbnailData);
            if (testConf != null) {
                testConf.setThumbnailData(thumbnailData);
            }

            startTest(thumbnailRequest, testConf.getTestType(), thumbnailData);
            scheduleFinalResults(thumbnailRequest, thumbnailData);

        } catch (Exception e) {
            log.error("Thumbnail test failed", e);
            sendError(ERR_INTERNAL_SERVER);
        }
    }

    private boolean validateImageOptions(List<ImageOption> imageOptions) {
        for (ImageOption option : imageOptions) {
            try {
                File file = supaBaseImageService.getFileFromPath(new URL(option.getFileUrl()));
                if (!thumbnailService.isValid(file)) {
                    return false;
                }
            } catch (MalformedURLException e) {
                log.error("Invalid image URL: {}", option.getFileUrl(), e);
                return false;
            }
        }
        return true;
    }

    private boolean validateVideoOwnership(UserData userData, String videoId) {
        String videoOwnerChannelId = youTubeService.getVideoOwnerChannelId(userData, videoId);
        String userChannelId = youTubeService.getUserChannelId(userData);

        if (videoOwnerChannelId == null) {
            sendError(ERR_VIDEO_NOT_FOUND);
            return false;
        }
        if (userChannelId == null) {
            sendError(ERR_USER_CHANNEL_NOT_FOUND);
            return false;
        }
        if (!userChannelId.equals(videoOwnerChannelId)) {
            sendError(ERR_UNAUTHORIZED_VIDEO_ACCESS);
            return false;
        }
        return true;
    }

    private void scheduleFinalResults(ThumbnailRequest thumbnailRequest, ThumbnailData thumbnailData) {
        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60_000L;
        taskScheduler.schedule(() -> {
            try {
                List<ImageOption> results = getTestResults(thumbnailData, thumbnailData.getTestConf().getCriterionOfWinner());
                messagingTemplate.convertAndSend(TOPIC_RESULT, results);
            } catch (Exception e) {
                log.error("Error sending final test results", e);
                sendError(ERR_ERROR_SENDING_RESULTS);
            }
        }, new java.util.Date(System.currentTimeMillis() + delayMillis));
    }

    @Async
    public void startTest(ThumbnailRequest thumbnailRequest, TestingType testingType, ThumbnailData thumbnailData) {
        log.info("Starting test with type: {}", testingType);
        List<ImageOption> imageOptions = thumbnailData.getImageOptions();
        List<String> texts = thumbnailRequest.getTexts();
        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60_000L;

        ThumbnailQueue thumbnailQueue = thumbnailQueueService.getQueue(thumbnailRequest.getVideoUrl());

        for (ImageOption imageOption : imageOptions) {
            thumbnailQueue.add(new ThumbnailQueueItem(thumbnailRequest.getVideoUrl(), imageOption));
        }
        log.info("Thumbnail queue initialized: {}", thumbnailQueue);

        int count = calculateTestCount(testingType, imageOptions, texts);
        log.info("Number of tests to run: {}", count);

        if (count == 0) {
            sendError(ERR_INVALID_INPUTS);
            return;
        }

        ThumbnailQueueItem queueItem;
        log.info("Processing queue items");

        while ((queueItem = thumbnailQueue.poll()) != null) {
            thumbnailQueue.delete(queueItem);
            queueItem.setActive(true);
            try {
                processSingleTestSync(thumbnailData, queueItem, delayMillis, testingType);
            } catch (Exception e) {
                log.error("Error during processing single test", e);
                sendError(ERR_INTERNAL_SERVER + ": " + e.getMessage());
            }
        }
        log.info("Finished processing queue");

        try {
            List<ImageOption> options = getTestResults(thumbnailData, thumbnailData.getTestConf().getCriterionOfWinner());
            thumbnailService.save(thumbnailData);
            messagingTemplate.convertAndSend(TOPIC_FINAL, options);
        } catch (Exception e) {
            log.error("Error sending final results", e);
            sendError(ERR_FINAL_RESULT_ERROR);
        }
    }

    private int calculateTestCount(TestingType testingType, List<ImageOption> imageOptions, List<String> texts) {
        return switch (testingType) {
            case THUMBNAIL -> imageOptions != null ? imageOptions.size() : 0;
            case TEXT -> texts != null ? texts.size() : 0;
            case THUMBNAILTEXT -> (imageOptions != null && texts != null && imageOptions.size() == texts.size()) ? imageOptions.size() : 0;
        };
    }

    private void processSingleTestSync(ThumbnailData thumbnailData, ThumbnailQueueItem queueItem, long delayMillis, TestingType testingType) {
        log.info("Processing single test sync");
        try {
            ImageOption imageOption = queueItem.getImageOption();
            String text = imageOption.getText();

            ThumbnailStats oldStats = imageOption.getThumbnailStats();
            if (oldStats == null) {
                oldStats = initializeEmptyStats(imageOption);
            }
            thumbnailService.save(thumbnailData);

            LocalDate startDate = LocalDate.now();

            try {
                if (testingType == TestingType.THUMBNAIL || testingType == TestingType.THUMBNAILTEXT) {
                    log.info("Uploading thumbnail");
                    File imageFile = supaBaseImageService.getFileFromPath(new URL(imageOption.getFileUrl()));

                    youTubeService.uploadThumbnail(thumbnailData, imageFile);
                    Thread.sleep(THUMBNAIL_UPLOAD_WAIT_MILLIS);
                    supaBaseImageService.deleteFileWithPath(imageFile);
                    log.info("Thumbnail upload completed");
                }
            } catch (IOException e) {
                log.error(ERR_THUMBNAIL_UPLOAD_FAILED, e);
                sendError(ERR_THUMBNAIL_UPLOAD_FAILED + ": " + e.getMessage());
                return;
            } catch (InterruptedException e) {
                log.error("Interrupted during thumbnail upload", e);
                sendError(ERR_INTERNAL_SERVER + ": " + e.getMessage());
                return;
            }

            if (testingType == TestingType.TEXT || testingType == TestingType.THUMBNAILTEXT) {
                String videoId = youTubeService.getVideoIdFromUrl(thumbnailData.getVideoUrl());
                log.info("Updating video title to: {}", text);
                youTubeService.updateVideoTitle(thumbnailData.getUser(), videoId, text);
                boolean titleUpdated = waitForTitleUpdate(thumbnailData.getUser(), videoId, text, DEFAULT_TITLE_UPDATE_TIMEOUT_MILLIS);
                if (!titleUpdated) {
                    log.error("Title update timed out");
                    sendError(ERR_ERROR_UPDATING_TITLE);
                    return;
                }
                log.info("Title update completed");
            }

            log.info("Waiting for test duration: {} ms", delayMillis);
            Thread.sleep(delayMillis);

            ThumbnailStats stats = youTubeAnalyticsService.getStats(thumbnailData.getUser(), startDate, queueItem);
            if (stats != null) {
                log.info("Received stats for thumbnail");
                imageOption.setThumbnailStats(stats);
                stats.setImageOption(imageOption);
                imageOption.setThumbnail(thumbnailData);

                thumbnailService.save(thumbnailData);
                log.info("Thumbnail data saved");

                messagingTemplate.convertAndSend(TOPIC_PROGRESS, imageOption);
            } else {
                log.warn("No stats received for thumbnail test");
                messagingTemplate.convertAndSend(TOPIC_PROGRESS, imageOption);
            }
        } catch (InterruptedException e) {
            log.error("Interrupted during test processing", e);
            sendError(ERR_INTERNAL_SERVER + ": " + e.getMessage());
        }
    }

    private ThumbnailStats initializeEmptyStats(ImageOption imageOption) {
        ThumbnailStats stats = new ThumbnailStats();
        stats.setViews(0);
        stats.setCtr(0.0);
        stats.setImpressions(0);
        stats.setAverageViewDuration(0.0);
        stats.setAdvCtr(0.0);
        stats.setComments(0);
        stats.setShares(0);
        stats.setLikes(0);
        stats.setSubscribersGained(0);
        stats.setAverageViewPercentage(0.0);
        stats.setTotalWatchTime(0L);
        stats.setImageOption(imageOption);
        imageOption.setThumbnailStats(stats);
        return stats;
    }

    private List<ImageOption> getTestResults(ThumbnailData thumbnailData, CriterionOfWinner criterion) {
        List<ImageOption> options = thumbnailData.getImageOptions();

        if (criterion == CriterionOfWinner.NONE || options == null || options.isEmpty()) {
            return options;
        }

        ImageOption winner = null;
        double maxMetric = Double.NEGATIVE_INFINITY;

        for (ImageOption option : options) {
            ThumbnailStats stats = option.getThumbnailStats();
            if (stats == null) {
                stats = initializeEmptyStats(option);
            }

            double value = switch (criterion) {
                case VIEWS -> stats.getViews();
                case AVD -> stats.getAverageViewDuration();
                case CTR -> stats.getCtr();
                case WATCH_TIME -> stats.getTotalWatchTime();
                case CTR_ADV -> stats.getCtr() * stats.getAverageViewDuration();
                default -> Double.NEGATIVE_INFINITY;
            };

            if (value > maxMetric) {
                maxMetric = value;
                winner = option;
            }
        }

        if (winner != null) {
            winner.setWinner(true);
        }

        return options;
    }

    private boolean waitForTitleUpdate(UserData user, String videoId, String expectedTitle, long timeoutMillis) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMillis) {
            String currentTitle = youTubeService.getVideoTitle(user, videoId);
            if (expectedTitle.equals(currentTitle)) {
                return true;
            }
            Thread.sleep(DEFAULT_TITLE_UPDATE_POLL_INTERVAL_MILLIS);
        }
        return false;
    }

    private void sendError(String errorMessage) {
        messagingTemplate.convertAndSend(TOPIC_ERROR, errorMessage);
    }
}
