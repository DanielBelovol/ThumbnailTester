package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.repositories.ThumbnailRepository;
import com.example.ThumbnailTester.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

@Service
public class ThumbnailService {
    private final SimpMessagingTemplate messagingTemplate;
    @Autowired
    private ThumbnailRepository thumbnailRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ImageParser imageParser;

    public ThumbnailService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void save(ThumbnailData thumbnailData) {
        thumbnailRepository.save(thumbnailData);
    }

    public boolean isValid(String fileBase64) {

        // Decode the base64 string to get the Image
        Image image = imageParser.getImageFromBase64(fileBase64);
        if (image == null) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Invalid base64 image data.");
            return false;
        }

        // Create a temporary file to save the image
        String tempFileName = generateTempFileName("thumbnail", "png");
        File tempFile = createTempFileFromImage(image, tempFileName);

        if (tempFile == null) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Failed to create a temporary file.");
            return false;
        }

        // Get the file size in MB
        Double size = getFileSizeMegaBytes(tempFile);
        if (size == null) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Failed to get file size.");
            tempFile.delete(); // Cleanup
            return false;
        }

        // Check if the file size is >= 2MB and if the aspect ratio is 16:9
        boolean valid = size >= 2 && is16by9(image);

        // Optionally delete the temporary file after validation
        tempFile.delete();

        return valid;
    }

    private String generateTempFileName(String prefix, String extension) {
        // Generate a more descriptive file name
        Random random = new Random();
        return prefix + "_" + System.currentTimeMillis() + random.nextInt(500)+"." + extension;
    }

    private File createTempFileFromImage(Image image, String fileName) {
        try {
            // Convert Image to BufferedImage
            BufferedImage bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);

            // Draw the Image into the BufferedImage
            Graphics2D g2d = bufferedImage.createGraphics();
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();

            // Create a temporary file and write the image to it
            File tempFile = File.createTempFile(fileName, null);
            ImageIO.write(bufferedImage, "png", tempFile); // Explicitly use "png" format

            return tempFile;
        } catch (IOException e) {
            messagingTemplate.convertAndSend("/topic/thumbnail/error", "Failed to create temporary file: " + e.getMessage());
            return null;
        }
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
