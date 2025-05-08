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
 * This service handles the logic for running tests on YouTube thumbnails, including:
 * - Uploading thumbnails
 * - Updating video titles
 * - Collecting analytics data
 * - Sending progress and result notifications via WebSocket
 */
@Service
public class ThumbnailTestService {
    private static final Logger log = LoggerFactory.getLogger(ThumbnailTestService.class);

    private static final long DEFAULT_TITLE_UPDATE_TIMEOUT_MILLIS = 100000L;
    private static final long DEFAULT_TITLE_UPDATE_POLL_INTERVAL_MILLIS = 5000L;
    private static final long THUMBNAIL_UPLOAD_WAIT_MILLIS = 10000L;

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
            log.info("User: {}", userData);

            ThumbnailTestConf testConf = thumbnailData.getTestConf();
            List<ImageOption> imageOptions = thumbnailData.getImageOptions();

            if (imageOptions == null || imageOptions.isEmpty()) {
                sendError("NoImagesProvided");
                return;
            }

            if (!validateImageOptions(imageOptions)) {
                sendError("UnsupportedImage");
                return;
            }

            String videoId = youTubeService.getVideoIdFromUrl(thumbnailData.getVideoUrl());
            if (videoId == null) {
                sendError("InvalidVideoUrl");
                return;
            }

            if (!validateVideoOwnership(userData, videoId)) {
                return; // Ошибки отправляются внутри метода
            }

            // Saving new user
            if (userData.getId() == null) {
                userData = userService.save(userData);
                thumbnailData.setUser(userData);
            }

            // Saving thumbnailData and make relations
            thumbnailData = thumbnailService.save(thumbnailData);
            if (testConf != null) {
                testConf.setThumbnailData(thumbnailData);
            }

            startTest(thumbnailRequest, testConf.getTestType(), thumbnailData);

            scheduleFinalResults(thumbnailRequest, thumbnailData);

        } catch (Exception e) {
            log.error("Thumbnail test failed", e);
            sendError("InternalServerError");
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
            sendError("VideoNotFound");
            return false;
        }
        if (userChannelId == null) {
            sendError("UserChannelNotFound");
            return false;
        }
        if (!userChannelId.equals(videoOwnerChannelId)) {
            sendError("UnauthorizedVideoAccess");
            return false;
        }
        return true;
    }

    private void scheduleFinalResults(ThumbnailRequest thumbnailRequest, ThumbnailData thumbnailData) {
        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;
        taskScheduler.schedule(() -> {
            try {
                List<ImageOption> results = getTestResults(thumbnailData, thumbnailData.getTestConf().getCriterionOfWinner());
                messagingTemplate.convertAndSend("/topic/thumbnail/result", results);
            } catch (Exception e) {
                log.error("Error sending final test results", e);
                sendError("ErrorSendingResults");
            }
        }, new java.util.Date(System.currentTimeMillis() + delayMillis));
    }

    @Async
    public void startTest(ThumbnailRequest thumbnailRequest, TestConfType testConfType, ThumbnailData thumbnailData) {
        log.info("startTest");
        List<ImageOption> imageOptions = thumbnailData.getImageOptions();
        List<String> texts = thumbnailRequest.getTexts();
        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;

        ThumbnailQueue thumbnailQueue = thumbnailQueueService.getQueue(thumbnailRequest.getVideoUrl());

        // Добавляем все imageOptions в очередь
        for (ImageOption imageOption : imageOptions) {
            thumbnailQueue.add(new ThumbnailQueueItem(thumbnailRequest.getVideoUrl(), imageOption));
        }
        log.info("thumbnailQueue: {}", thumbnailQueue);

        int count = calculateTestCount(testConfType, imageOptions, texts);
        log.info("count: {}", count);

        if (count == 0) {
            sendError("InvalidInputs");
            return;
        }

        ThumbnailQueueItem queueItem;
        log.info("While started");

        while ((queueItem = thumbnailQueue.poll()) != null) {
            thumbnailQueue.delete(queueItem);
            queueItem.setActive(true);
            try {
                processSingleTestSync(thumbnailData, queueItem, delayMillis, testConfType);
            } catch (Exception e) {
                log.error("Error during processSingleTestSync", e);
                sendError("InternalServerError: " + e.getMessage());
            }
        }
        log.info("while ended");

        try {
            List<ImageOption> options = getTestResults(thumbnailData, thumbnailData.getTestConf().getCriterionOfWinner());
            thumbnailService.save(thumbnailData);
            messagingTemplate.convertAndSend("/topic/thumbnail/final", options);
        } catch (Exception e) {
            log.error("Final result error", e);
            sendError("FinalResultError");
        }
    }

    private int calculateTestCount(TestConfType testConfType, List<ImageOption> imageOptions, List<String> texts) {
        return switch (testConfType) {
            case THUMBNAIL -> imageOptions != null ? imageOptions.size() : 0;
            case TEXT -> texts != null ? texts.size() : 0;
            case THUMBNAILTEXT ->
                    (imageOptions != null && texts != null && imageOptions.size() == texts.size()) ? imageOptions.size() : 0;
        };
    }

    private void processSingleTestSync(ThumbnailData thumbnailData, ThumbnailQueueItem queueItem, long delayMillis, TestConfType testConfType) {
        log.info("processSingleTestSync");
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
                if (testConfType == TestConfType.THUMBNAIL || testConfType == TestConfType.THUMBNAILTEXT) {
                    log.info("upload thumbnail started");
                    File imageFile = supaBaseImageService.getFileFromPath(new URL(imageOption.getFileUrl()));

                    youTubeService.uploadThumbnail(thumbnailData, imageFile);
                    Thread.sleep(THUMBNAIL_UPLOAD_WAIT_MILLIS);
                    supaBaseImageService.deleteFileWithPath(imageFile);
                    log.info("upload thumbnail completed");
                }
                // ... остальной код
            } catch (IOException e) {
                log.error("Thumbnail upload failed", e);
                sendError("Thumbnail upload failed: " + e.getMessage());
                return; // Прекращаем тест для этого варианта
            } catch (InterruptedException e) {
                log.error("Error in processSingleTestSync", e);
                sendError("InternalServerError: " + e.getMessage());
                return;
            }

            if (testConfType == TestConfType.TEXT || testConfType == TestConfType.THUMBNAILTEXT) {
                String videoId = youTubeService.getVideoIdFromUrl(thumbnailData.getVideoUrl());
                log.info("videoID: {}", videoId);
                youTubeService.updateVideoTitle(thumbnailData.getUser(), videoId, text);
                boolean isTitleHasBeenUpdated = waitForTitleUpdate(thumbnailData.getUser(), videoId, text, DEFAULT_TITLE_UPDATE_TIMEOUT_MILLIS);
                if(!isTitleHasBeenUpdated){
                    log.error("Title has not been updated");
                    sendError("Error with updating title");
                    return;
                }
                log.info("update ended");
            }

            log.info("waiting started");
            Thread.sleep(delayMillis);

            ThumbnailStats stats = youTubeAnalyticsService.getStats(thumbnailData.getUser(), startDate, queueItem);
            if (stats != null) {
                log.info("stats != null");
                imageOption.setThumbnailStats(stats);
                stats.setImageOption(imageOption);
                imageOption.setThumbnail(thumbnailData);

                thumbnailService.save(thumbnailData);
                log.info("thumbnail saved");

                messagingTemplate.convertAndSend("/topic/thumbnail/progress", imageOption);
            } else {
                log.warn("No stats received for thumbnail test");
                messagingTemplate.convertAndSend("/topic/thumbnail/progress", imageOption);
            }
        } catch (InterruptedException e) {
            log.error("Error in processSingleTestSync", e);
            sendError("InternalServerError: " + e.getMessage());
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
        double maxMetric = -1;

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
                default -> -1;
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
        messagingTemplate.convertAndSend("/topic/thumbnail/error", errorMessage);
    }
}