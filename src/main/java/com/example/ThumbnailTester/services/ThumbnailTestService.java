package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.Request.UserRequest;
import com.example.ThumbnailTester.data.thumbnail.*;
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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.example.ThumbnailTester.data.thumbnail.CriterionOfWinner.*;

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
    public void runThumbnailTest(ThumbnailRequest thumbnailRequest) {
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

            // 7. Getting the criterion of winner
            CriterionOfWinner finalCriterionOfWinner = getCriterionOfWinner(thumbnailRequest);

            // 8. Start the test
            startTest(thumbnailRequest, testConf.getTestType(), thumbnailData);

            // 9. Schedule sending final test results
            long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;


            taskScheduler.schedule(() -> {
                try {
                    messagingTemplate.convertAndSend("/topic/thumbnail/result", getTestResults(thumbnailData, finalCriterionOfWinner));
                } catch (Exception e) {
                    messagingTemplate.convertAndSend("/topic/thumbnail/error", "ErrorSendingResults");
                }
            }, new java.util.Date(System.currentTimeMillis() + delayMillis));

        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "InternalServerError");
        }
    }

    private CriterionOfWinner getCriterionOfWinner(ThumbnailRequest thumbnailRequest) {
        CriterionOfWinner criterionOfWinner = NONE;
        switch (thumbnailRequest.getTestConfRequest().getCriterionOfWinner()) {
            case "VIEWS" -> criterionOfWinner = VIEWS;
            case "AVD" -> criterionOfWinner = AVD;
            case "CTR" -> criterionOfWinner = CTR;
            case "WATCH_TIME" -> criterionOfWinner = CriterionOfWinner.WATCH_TIME;
            case "CTR_ADV" -> criterionOfWinner = CriterionOfWinner.CTR_ADV;
        }
        return criterionOfWinner;
    }


    @Async
    public void startTest(ThumbnailRequest thumbnailRequest, TestConfType testConfType, ThumbnailData thumbnailData) {
        List<ImageOption> files64 = thumbnailRequest.getImageOptions();
        List<String> texts = thumbnailRequest.getTexts();
        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;

        // Initialize queue for the video URL
        ThumbnailQueue thumbnailQueue = thumbnailQueueService.getQueue(thumbnailRequest.getVideoUrl());

        // Add all image options to the queue
        for (ImageOption imageOption : thumbnailData.getImageOptions()) {
            thumbnailQueue.add(new ThumbnailQueueItem(thumbnailRequest.getVideoUrl(), imageOption));
        }

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

        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();

        ThumbnailQueueItem thumbnailQueueItem;
        while ((thumbnailQueueItem = thumbnailQueue.poll()) != null) {
            final ThumbnailQueueItem currentItem = thumbnailQueueItem;
            thumbnailQueue.delete(currentItem);
            currentItem.setActive(true);
            futures.add(processSingleTest(thumbnailData, currentItem, delayMillis, testConfType));
        }

        // После всех тестов определяем победителя и отправляем статистику
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            try {
                List<ImageOption> options = getTestResults(thumbnailData, getCriterionOfWinner(thumbnailRequest));
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
                ThumbnailStats stats = youTubeAnalyticsService.getStats(thumbnailData.getUser(), LocalDate.now(), thumbnailQueueItem);
                if (stats != null) {
                    imageOption.setThumbnailStats(stats);
                    stats.setImageOption(imageOption);
                    imageOption.setThumbnail(thumbnailData);

                    thumbnailService.save(thumbnailData);

                    // Send intermediate result via WebSocket
                    messagingTemplate.convertAndSend("/topic/thumbnail/progress", imageOption);
                }
            } catch (InterruptedException e) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "InternalServerError");
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