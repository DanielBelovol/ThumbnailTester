package com.example.ThumbnailTester.controller;

import com.example.ThumbnailTester.Request.ThumbnailRequest;
import com.example.ThumbnailTester.dto.ImageOption;
import com.example.ThumbnailTester.dto.ThumbnailQueue;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import com.example.ThumbnailTester.services.ThumbnailQueueService;
import com.example.ThumbnailTester.services.ThumbnailTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.io.IOException;

@Controller
public class WebSocketController {
    private final SimpMessagingTemplate messagingTemplate;
    @Autowired
    private ThumbnailTestService thumbnailTestService;
    @Autowired
    private ThumbnailQueueService thumbnailQueueService;

    public WebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/thumbnail/test")
    public void handleTestMessage(@Payload ThumbnailRequest request) {
        try {
            // verify if there are images in the request
            if (request.getImageOptions() == null || request.getImageOptions().isEmpty()) {
                // sending error message if no images are provided
                messagingTemplate.convertAndSend("/topic/thumbnail/error", "NoImagesProvided");
                return;
            }

            // iterate through the image options
            for (ImageOption imageOption : request.getImageOptions()) {
                // create a ThumbnailQueueItem
                ThumbnailQueueItem queueItem = new ThumbnailQueueItem(request.getVideoUrl(), imageOption);
                // add the item to the queue
                thumbnailQueueService.addToQueue(queueItem.getVideoUrl(), queueItem);
            }

            // start the thumbnail test
            thumbnailTestService.runThumbnailTest(request);

        } catch (IOException e) {
            // if an error occurs, send an error message
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "ErrorProcessingRequest");
            throw new RuntimeException("Error processing the thumbnail test", e);
        }
    }

    @MessageMapping("/remove-testingItem")
    public void handleRemoveThumbnail(@Payload Long imageOptionId, @Payload String videoUrl) {
        // receive the queue item from the imageId and videoUrl
        ThumbnailQueue queue = thumbnailQueueService.findItemByVideoUrl(videoUrl);
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
