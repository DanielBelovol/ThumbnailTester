package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.repositories.ThumbnailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;

@Service
public class ThumbnailService {
    private final SimpMessagingTemplate messagingTemplate;
    @Autowired
    private ThumbnailRepository thumbnailRepository;

    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);

    public ThumbnailService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public ThumbnailData save(ThumbnailData thumbnailData) {
        thumbnailRepository.save(thumbnailData);
        return thumbnailData;
    }

    public boolean isValid(File fileImage) {

        // Get the file size in MB
        Double size = getFileSizeMegaBytes(fileImage);
        if (size == null) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Failed to get file size.");
            fileImage.delete(); // Cleanup
            return false;
        }

        // Check if the file size is >= 2MB and if the aspect ratio is 16:9
        Image image = null;
        try {
            image = ImageIO.read(fileImage);
        } catch (IOException e) {
            log.error("error while reading file", e);
        }
        boolean valid = size >= 2 && is16by9(image);

        // Optionally delete the temporary file after validation
        fileImage.delete();

        return true;
    }

    private Double getFileSizeMegaBytes(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        long sizeInBytes = file.length();
        return sizeInBytes / (1024.0 * 1024.0); // Convert bytes to MB
    }

    private boolean is16by9(Image image) {
        // Check aspect ratio 16:9 (width:height)
        int width = image.getWidth(null);
        int height = image.getHeight(null);
        return width == 16 * height / 9;
    }
}
