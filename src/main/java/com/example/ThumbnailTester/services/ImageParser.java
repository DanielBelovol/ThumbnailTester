package com.example.ThumbnailTester.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;


@Service
public class ImageParser {
    private final SimpMessagingTemplate messagingTemplate;
    private static final Logger log = LoggerFactory.getLogger(ImageParser.class);

    public ImageParser(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }



    // Метод для проверки корректности Base64 строки
    private boolean isValidBase64(String base64) {
        try {
            Base64.getDecoder().decode(base64);  // Попробуем декодировать
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public File getFileFromBase64(String base64) {
//        File tempDir = new File("temp");
//        if (!tempDir.exists()) {
//            log.info("Creating temporary directory 'temp' for storing the file.");
//            tempDir.mkdirs();
//        }
//
//        String safeName = String.valueOf(base64.hashCode());
//        File file = new File(tempDir, safeName + ".jpg");
//
//        // Проверка прав на запись в директорию
//        if (!tempDir.canWrite()) {
//            log.error("Cannot write to directory: " + tempDir.getAbsolutePath());
//            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Cannot write to directory");
//            return null;
//        }
//
//        byte[] decodedBytes = Base64.getDecoder().decode(base64);
//        try (FileOutputStream fos = new FileOutputStream(file)) {
//            log.info("Writing decoded Base64 string to file: " + file.getAbsolutePath());
//            fos.write(decodedBytes);
//            log.info("Base64 string successfully written to file.");
//        } catch (IOException e) {
//            log.error("IOException occurred while writing Base64 string to file: " + e.getMessage(), e);
//            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Failed to write Base64 string to file");
//        }
        File file1 = new File("/app/resources/image1.jpeg");
        if (!file1.exists()) {
            log.error("File does not exist at path: " + file1.getAbsolutePath());
        }
        return file1;
    }

}
