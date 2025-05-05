package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.data.thumbnail.*;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ImageOption;
import com.example.ThumbnailTester.dto.ThumbnailQueue;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import com.example.ThumbnailTester.repositories.ThumbnailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
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
    SupaBaseImageService supaBaseImageService;
    @Autowired
    private YouTubeService youTubeService;
    @Autowired
    private YouTubeAnalyticsService youTubeAnalyticsService;
    @Autowired
    private ImageParser imageParser;
    @Autowired
    private ThumbnailRepository thumbnailRepository;
    private static final Logger log = LoggerFactory.getLogger(ThumbnailTestService.class);


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
    public void runThumbnailTest(ThumbnailRequest thumbnailRequest, ThumbnailData thumbnailData) {
        try {
            // 1. Receive user data
            UserData userData = thumbnailData.getUser();
            log.info("User: " + userData.toString());

            // 2. Map test configuration and thumbnail data
            log.info("mapping");
            ThumbnailTestConf testConf = thumbnailData.getTestConf();

            List<ImageOption> imageOptions = thumbnailData.getImageOptions();

            // 3. Validate image options
            if (imageOptions == null || imageOptions.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "NoImagesProvided");
                return;
            }

            for (ImageOption option : imageOptions) {
                log.info("validation started");
                if (!thumbnailService.isValid(supaBaseImageService.getFileFromPath(new URL(option.getFileUrl())))) {
                    messagingTemplate.convertAndSend("/topic/thumbnail/error", "UnsupportedImage");
                    return;
                }
                log.info("validation1 completed");
            }
            log.info("validation completed");

            // 4. Check video ownership
            log.info("checking videoOwner");
            String videoId = youTubeService.getVideoIdFromUrl(thumbnailData.getVideoUrl());
            log.info("videoId" + videoId);
            String videoOwnerChannelId = youTubeService.getVideoOwnerChannelId(userData, videoId);
            log.info("videoOwnerChannelId" + videoOwnerChannelId);
            String userChannelId = youTubeService.getUserChannelId(userData);
            log.info("userChannelId" + userChannelId);

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

            // 5. Save thumbnail test data
            log.info("saving thumbnaildata");
            thumbnailService.save(thumbnailData);

            // 6. Start the test
            log.info("startTest");
            startTest(thumbnailRequest, testConf.getTestType(), thumbnailData);

            // 7. Schedule sending final test results
            long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;

            taskScheduler.schedule(() -> {
                try {
                    messagingTemplate.convertAndSend("/topic/thumbnail/result", getTestResults(thumbnailData, thumbnailData.getTestConf().getCriterionOfWinner()));
                } catch (Exception e) {
                    messagingTemplate.convertAndSend("/topic/thumbnail/error", "ErrorSendingResults");
                }
            }, new java.util.Date(System.currentTimeMillis() + delayMillis));

        } catch (Exception e) {
            log.error("Thumbnail test failed", e); // добавь это
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "InternalServerError");
        }
    }


    @Async
    public void startTest(ThumbnailRequest thumbnailRequest, TestConfType testConfType, ThumbnailData thumbnailData) {
        log.info("startTest");
        List<ImageOption> imageOptions = thumbnailData.getImageOptions();
        List<String> texts = thumbnailRequest.getTexts();
        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;
        log.info("delayMillis" + delayMillis);

        // Initialize queue for the video URL
        ThumbnailQueue thumbnailQueue = thumbnailQueueService.getQueue(thumbnailRequest.getVideoUrl());

        // Add all image options to the queue
        for (ImageOption imageOption : thumbnailData.getImageOptions()) {
            thumbnailQueue.add(new ThumbnailQueueItem(thumbnailRequest.getVideoUrl(), imageOption));
        }
        log.info("thumbnailQueue" + thumbnailQueue);

        // Determine the number of tests to run based on the test configuration type
        int count = switch (testConfType) {
            case THUMBNAIL -> imageOptions != null ? imageOptions.size() : 0;
            case TEXT -> texts != null ? texts.size() : 0;
            case THUMBNAILTEXT ->
                    (imageOptions != null && texts != null && imageOptions.size() == texts.size()) ? imageOptions.size() : 0;
        };
        log.info("count" + count);

        if (count == 0) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "InvalidInputs");
            return;
        }

        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        log.info("futures" + futures);

        ThumbnailQueueItem thumbnailQueueItem;
        log.info("While started");
        while ((thumbnailQueueItem = thumbnailQueue.poll()) != null) {
            final ThumbnailQueueItem currentItem = thumbnailQueueItem;
            thumbnailQueue.delete(currentItem);
            currentItem.setActive(true);
            futures.add(processSingleTest(thumbnailData, currentItem, delayMillis, testConfType));
        }
        log.info("while ended");

        // После всех тестов определяем победителя и отправляем статистику
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            try {
                List<ImageOption> options = getTestResults(thumbnailData, thumbnailData.getTestConf().getCriterionOfWinner());
                thumbnailService.save(thumbnailData); // Сохраняем обновлённые isWinner
                messagingTemplate.convertAndSend("/topic/thumbnail/final", options);
            } catch (Exception e) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "FinalResultError");
            }
        });
    }

    private CompletableFuture<Void> processSingleTest(
            ThumbnailData thumbnailData,
            ThumbnailQueueItem thumbnailQueueItem,
            long delayMillis,
            TestConfType testConfType
    ) {
        return CompletableFuture.runAsync(() -> {
            log.info("processSingleTest");
            try {
                ImageOption imageOption = thumbnailQueueItem.getImageOption();
                String text = imageOption.getText();
                log.info("inf from req");

                // Update the thumbnail if required
                if (testConfType == TestConfType.THUMBNAIL || testConfType == TestConfType.THUMBNAILTEXT) {
                    log.info("upload thumbnail started");
                    File imageFile = supaBaseImageService.getFileFromPath(new URL(imageOption.getFileUrl()));

                    log.info("file received");
                    youTubeService.uploadThumbnail(thumbnailData, imageFile);
                    Thread.sleep(10000);
                    supaBaseImageService.deleteFileWithPath(imageFile);
                    log.info("File has been deleted");
                    log.info("upload thumbnail");
                }


                // Update the video title if required
                log.info("update the video title if req");
                if (testConfType == TestConfType.THUMBNAILTEXT || testConfType == TestConfType.TEXT) {
                    youTubeService.updateVideoTitle(thumbnailData.getUser(), thumbnailData.getVideoUrl(), text);
                    log.info("update ended");
                }


                // Wait for the specified delay
                log.info("waiting started");
                Thread.sleep(delayMillis);

                // Fetch and save statistics
                log.info("fetch and save stats started");
                ThumbnailStats stats = youTubeAnalyticsService.getStats(thumbnailData.getUser(), LocalDate.now(), thumbnailQueueItem);
                if (stats != null) {
                    log.info("stats != null");
                    imageOption.setThumbnailStats(stats);
                    stats.setImageOption(imageOption);
                    imageOption.setThumbnail(thumbnailData);

                    thumbnailService.save(thumbnailData);
                    log.info("thumbnail saved");

                    // Send intermediate result via WebSocket
                    messagingTemplate.convertAndSend("/topic/thumbnail/progress", imageOption);
                }
            } catch (InterruptedException | MalformedURLException e) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "InternalServerError"+e.getMessage());
            }
        });
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
            if (stats == null) continue;

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
}