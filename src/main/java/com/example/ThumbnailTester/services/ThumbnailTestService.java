package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.Request.UserRequest;
import com.example.ThumbnailTester.data.thumbnail.TestConfType;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailTestConf;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ImageOption;
import com.example.ThumbnailTester.mapper.Mapper;
import com.example.ThumbnailTester.result.ThumbnailResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ThumbnailTestService {
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;
    @Autowired
    private ThumbnailService thumbnailService;
    @Autowired
    private UserService userService;
    @Autowired
    private ThumbnailStatsService thumbnailStatsService;
    @Autowired
    private YouTubeService youTubeService;
    @Autowired
    private YouTubeAnalyticsService youTubeAnalyticsService;
    @Autowired
    private ImageParser imageParser;
    @Autowired
    private Mapper mapper;

    public ThumbnailTestService(SimpMessagingTemplate messagingTemplate, TaskScheduler taskScheduler) {
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
    }

    @Async
    public void runThumbnailTest(ThumbnailRequest thumbnailRequest) throws IOException {
        UserRequest userRequest = thumbnailRequest.getUserDTO();
        UserData userData = userService.getByGoogleId(userRequest.getGoogleId());
        ThumbnailTestConf testConf = mapper.testConfRequestToDTO(thumbnailRequest.getTestConfRequest());
        ThumbnailData thumbnailData = mapper.thumbnailRequestToData(thumbnailRequest);
        List<ImageOption> imageOptions = thumbnailRequest.getImageOptions();

        if (imageOptions == null || imageOptions.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "NoImagesProvided");
            return;
        }
        //verifying that image is supported
        for(ImageOption option:imageOptions){
            if(!thumbnailService.isValid(option.getFileBase64())){
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "UnsupportedImage");
                return;
            }
        }
        // register user
        if(!userService.isExistById(userData.getId())){
            userData = new UserData(userRequest.getGoogleId(), userRequest.getRefreshToken());
        }
        // register Thumbnail
        thumbnailService.save(thumbnailData);


        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes()*60*1000L;
        UserData finalUserData = userData;

        startTest(thumbnailRequest, testConf.getTestType(), thumbnailData);

        taskScheduler.schedule(() -> {
            // 💡 Этот код выполнится только ПОСЛЕ таймера



            // например, тут можно собрать статистику (views, CTR)
            ThumbnailResult result = new ThumbnailResult();
            result.setImageResultList(null);
            result.setVideoUrl(thumbnailRequest.getVideoUrl());
            result.setTestConf(testConf);
            result.setUser(finalUserData);
            // 🔥 Отправляем по WebSocket
            messagingTemplate.convertAndSend("/topic/thumbnail/result", result);
        }, new java.util.Date(System.currentTimeMillis() + delayMillis));
    }
    @Async
    public void startTest(ThumbnailRequest thumbnailRequest, TestConfType testConfType, ThumbnailData thumbnailData) throws IOException {
        List<ImageOption> files64 = thumbnailRequest.getImageOptions();
        List<String> texts = thumbnailRequest.getTexts();



        long delayMillis = thumbnailRequest.getTestConfRequest().getTestingByTimeMinutes() * 60 * 1000L;

        // Асинхронная цепочка обработки изображений
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        if(testConfType==TestConfType.THUMBNAIL){
            //Validate
            if (files64 == null || files64.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "NoImagesProvided");
                return;
            }

            for (int i = 0; i < files64.size(); i++) {
                final int index = i;
                final String base64 = files64.get(i).getFileBase64();

                chain = chain.thenComposeAsync(prev -> processSingleImage(thumbnailData,base64, index, delayMillis));
            }
        } else if (testConfType==TestConfType.TEXT) {
            //Validate

            if(texts == null || texts.isEmpty()){
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "NoTextsProvided");
                return;
            }

            for (int i = 0; i < texts.size(); i++) {
                final int index = i;
                final String text = texts.get(i);

                chain = chain.thenComposeAsync(prev -> processSingleText(thumbnailData,text, index, delayMillis));
            }
        } else if (testConfType==TestConfType.THUMBNAILTEXT) {

        }


        // Когда все завершится
        chain.thenRun(() -> messagingTemplate.convertAndSend("/topic/thumbnail/done", "Test for all images completed"));
    }

    private CompletableFuture<Void> processSingleImage(ThumbnailData thumbnailData,String base64, int index, long delayMillis) {
        return CompletableFuture.runAsync(() -> {
            try {
                File imageFile = imageParser.getFileFromBase64(base64);
                youTubeService.uploadThumbnail(thumbnailData,imageFile);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<Void> processSingleText(ThumbnailData thumbnailData,String text, int index, long delayMillis) {
        return CompletableFuture.runAsync(() -> {
            try {
                youTubeService.updateVideoTitle(thumbnailData.getUser(),thumbnailData.getVideoUrl(), text);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
