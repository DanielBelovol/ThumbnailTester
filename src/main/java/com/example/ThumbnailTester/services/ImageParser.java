package com.example.ThumbnailTester.services;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;


@Service
public class ImageParser {
    private final SimpMessagingTemplate messagingTemplate;

    public ImageParser(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public Image getImageFromBase64(String base64) {
        try {
            return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        } catch (IOException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Failed to decode Base64 string to image");
        }
        return null;
    }

    public File getFileFromBase64(String base64) {
        File tempDir = new File("temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        String safeName = String.valueOf(base64.hashCode());
        File file = new File(tempDir, safeName + ".jpg");

        byte[] decodedBytes = Base64.getDecoder().decode(base64);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(decodedBytes);
        } catch (IOException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Failed to write Base64 string to file");
        }
        return file;
    }
}
