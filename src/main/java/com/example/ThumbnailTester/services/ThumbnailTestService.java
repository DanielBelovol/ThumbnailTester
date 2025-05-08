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
    private UserService userService;
    @Autowired
    private YouTubeAnalyticsService youTubeAnalyticsService;
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
            // Save user first if needed
            UserData user = thumbnailData.getUser();
            if (user.getId() == null) {
                user = userService.save(user);
                thumbnailData.setUser(user);
            }

            // Save thumbnailData first
            thumbnailData = thumbnailService.save(thumbnailData);

            // Set bidirectional association
            ThumbnailTestConf testconf = thumbnailData.getTestConf();
            if (testconf != null) {
                testconf.setThumbnailData(thumbnailData);
            }


            // 6. Start the test
            log.info("startTest");
            startTest(thumbnailRequest, testconf.getTestType(), thumbnailData);

            // 7. Schedule sending final test results
            long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;

            ThumbnailData finalThumbnailData = thumbnailData;
            ThumbnailData finalThumbnailData1 = thumbnailData;
            taskScheduler.schedule(() -> {
                try {
                    messagingTemplate.convertAndSend("/topic/thumbnail/result", getTestResults(finalThumbnailData, finalThumbnailData1.getTestConf().getCriterionOfWinner()));
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
        List imageOptions = thumbnailData.getImageOptions();
        List texts = thumbnailRequest.getTexts();
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

        ThumbnailQueueItem thumbnailQueueItem;
        log.info("While started");

// Последовательное выполнение тестов
        while ((thumbnailQueueItem = thumbnailQueue.poll()) != null) {
            thumbnailQueue.delete(thumbnailQueueItem);
            thumbnailQueueItem.setActive(true);
            try {
                // Синхронно вызываем метод обработки теста
                processSingleTestSync(thumbnailData, thumbnailQueueItem, delayMillis, testConfType);
            } catch (Exception e) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "InternalServerError: " + e.getMessage());
                log.error("Error during processSingleTestSync", e);
            }
        }
        log.info("while ended");

// После всех тестов определяем победителя и отправляем статистику
        try {
            List<ImageOption> options = getTestResults(thumbnailData, thumbnailData.getTestConf().getCriterionOfWinner());
            thumbnailService.save(thumbnailData); // Сохраняем обновлённые isWinner
            messagingTemplate.convertAndSend("/topic/thumbnail/final", options);
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "FinalResultError");
        }
    }

    private void processSingleTestSync(ThumbnailData thumbnailData, ThumbnailQueueItem thumbnailQueueItem, long delayMillis, TestConfType testConfType) {
        log.info("processSingleTestSync");
        try {
            ImageOption imageOption = thumbnailQueueItem.getImageOption();
            String text = imageOption.getText();

            // Получаем старую статистику (если нужна)
            ThumbnailStats oldStats = imageOption.getThumbnailStats();
            if (oldStats == null) {
                oldStats = new ThumbnailStats();
                // Инициализация полей нулями
                oldStats.setViews(0);
                oldStats.setCtr(0.0);
                oldStats.setImpressions(0);
                oldStats.setAverageViewDuration(0.0);
                oldStats.setAdvCtr(0.0);
                oldStats.setComments(0);
                oldStats.setShares(0);
                oldStats.setLikes(0);
                oldStats.setSubscribersGained(0);
                oldStats.setAverageViewPercentage(0.0);
                oldStats.setTotalWatchTime(0L);

                imageOption.setThumbnailStats(oldStats);
                oldStats.setImageOption(imageOption);
            }
            thumbnailService.save(thumbnailData);


            // Начальная дата для сбора статистики (например, сегодня)
            LocalDate startDate = LocalDate.now();

            // Обновляем миниатюру, если нужно
            if (testConfType == TestConfType.THUMBNAIL || testConfType == TestConfType.THUMBNAILTEXT) {
                log.info("upload thumbnail started");
                File imageFile = supaBaseImageService.getFileFromPath(new URL(imageOption.getFileUrl()));

                log.info("file received");

                youTubeService.uploadThumbnail(thumbnailData, imageFile);
                Thread.sleep(10000); // Ждем, чтобы изменения вступили в силу
                supaBaseImageService.deleteFileWithPath(imageFile);
                log.info("File has been deleted");
                log.info("upload thumbnail");
            }

            // Обновляем заголовок видео, если нужно
            if (testConfType == TestConfType.THUMBNAILTEXT || testConfType == TestConfType.TEXT) {
                youTubeService.updateVideoTitle(thumbnailData.getUser(), youTubeService.getVideoIdFromUrl(thumbnailData.getVideoUrl()), text);
                Thread.sleep(10000);
                log.info("update ended");
            }

            // Ждем заданное время для сбора статистики
            log.info("waiting started");
            Thread.sleep(delayMillis);

            // Получаем статистику с YouTube Analytics
            ThumbnailStats stats = youTubeAnalyticsService.getStats(thumbnailData.getUser(), startDate, thumbnailQueueItem);
            if (stats != null) {
                log.info("stats != null");
                // Обновляем статистику в ImageOption
                imageOption.setThumbnailStats(stats);
                stats.setImageOption(imageOption);
                imageOption.setThumbnail(thumbnailData);

                // Сохраняем изменения
                thumbnailService.save(thumbnailData);
                log.info("thumbnail saved");

                // Отправляем промежуточный результат через WebSocket
                messagingTemplate.convertAndSend("/topic/thumbnail/progress", imageOption);
            } else {
                log.warn("No stats received for thumbnail test");
                // Если статистика не получена, отправляем объект с нулями
                messagingTemplate.convertAndSend("/topic/thumbnail/progress", imageOption);
            }
        } catch (InterruptedException | MalformedURLException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "InternalServerError: " + e.getMessage());
            log.error("Error in processSingleTestSync", e);
        }
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
                stats = new ThumbnailStats();
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
            }
            thumbnailService.save(thumbnailData);

            // теперь используйте stats дальше
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