package com.example.ThumbnailTester.controller;
import com.example.ThumbnailTester.Request.ThumbnailRequest; import com.example.ThumbnailTester.data.thumbnail.ThumbnailData; import com.example.ThumbnailTester.dto.ImageOption; import com.example.ThumbnailTester.dto.ThumbnailQueue; import com.example.ThumbnailTester.dto.ThumbnailQueueItem; import com.example.ThumbnailTester.mapper.Mapper; import com.example.ThumbnailTester.services.ThumbnailQueueService; import com.example.ThumbnailTester.services.ThumbnailTestService; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.messaging.handler.annotation.MessageMapping; import org.springframework.messaging.handler.annotation.Payload; import org.springframework.messaging.simp.SimpMessagingTemplate; import org.springframework.stereotype.Controller;
@Controller public class WebSocketController { private final SimpMessagingTemplate messagingTemplate; private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);
    @Autowired
    private ThumbnailTestService thumbnailTestService;

    @Autowired
    private ThumbnailQueueService thumbnailQueueService;

    @Autowired
    private Mapper mapper;

    public WebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/thumbnail/test")
    public void handleTestMessage(@Payload ThumbnailRequest request) {
        log.info("Received thumbnail request: {}", request.getVideoUrl());

        if (request.getImages() == null || request.getImages().isEmpty()) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "NoImagesProvided");
            return;
        }

        ThumbnailData thumbnailData = mapper.thumbnailRequestToData(request);

        if (thumbnailData.getTestConf() == null) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "InvalidTestConfiguration");
            return;
        }

        for (ImageOption imageOption : thumbnailData.getImageOptions()) {
            ThumbnailQueueItem queueItem = new ThumbnailQueueItem(request.getVideoUrl(), imageOption);
            thumbnailQueueService.addToQueue(queueItem.getVideoUrl(), queueItem);
        }

        thumbnailTestService.runThumbnailTest(request, thumbnailData);
    }

    @MessageMapping("/remove-testingItem")
    public void handleRemoveThumbnail(@Payload Long imageOptionId, @Payload String videoUrl) {
        ThumbnailQueue queue = thumbnailQueueService.findItemByVideoUrl(videoUrl);
        if (queue == null) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "QueueNotFound");
            return;
        }

        ThumbnailQueueItem itemToRemove = queue.findByImageId(imageOptionId);

        if (itemToRemove == null) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "ItemNotFound");
            return;
        }

        if (itemToRemove.isActive()) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "ItemIsNowTesting");
            return;
        }

        thumbnailQueueService.deleteFromQueue(videoUrl, itemToRemove);
    }
}