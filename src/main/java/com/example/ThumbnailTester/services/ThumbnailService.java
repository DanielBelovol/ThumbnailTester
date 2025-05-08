package com.example.ThumbnailTester.services;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailData; import com.example.ThumbnailTester.repositories.ThumbnailRepository; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.messaging.simp.SimpMessagingTemplate; import org.springframework.stereotype.Service;
import javax.imageio.ImageIO; import java.awt.*; import java.io.File; import java.io.IOException;
@Service public class ThumbnailService { private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);
    private static final String TOPIC_ERROR = "/topic/thumbnail/error";
    private static final double MIN_FILE_SIZE_MB = 2.0;
    private static final int ASPECT_RATIO_WIDTH = 16;
    private static final int ASPECT_RATIO_HEIGHT = 9;

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ThumbnailRepository thumbnailRepository;

    public ThumbnailService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Saves the thumbnail data entity.
     *
     * @param thumbnailData the thumbnail data to save
     * @return the saved thumbnail data
     */
    public ThumbnailData save(ThumbnailData thumbnailData) {
        thumbnailRepository.save(thumbnailData);
        return thumbnailData;
    }

    /**
     * Validates the image file by checking its size and aspect ratio.
     *
     * @param fileImage the image file to validate
     * @return true if valid, false otherwise
     */
    public boolean isValid(File fileImage) {
        Double sizeMb = getFileSizeMegaBytes(fileImage);
        if (sizeMb == null) {
            sendError("Failed to get file size.");
            safeDeleteFile(fileImage);
            return false;
        }

        Image image = null;
        try {
            image = ImageIO.read(fileImage);
        } catch (IOException e) {
            log.error("Error reading image file", e);
            safeDeleteFile(fileImage);
            return false;
        }

        if (image == null) {
            log.error("Image could not be read or is null");
            safeDeleteFile(fileImage);
            return false;
        }

        boolean valid = sizeMb >= MIN_FILE_SIZE_MB && isAspectRatio16by9(image);

        safeDeleteFile(fileImage);

        return valid;
    }

    /**
     * Gets the file size in megabytes.
     *
     * @param file the file to check
     * @return size in MB or null if file does not exist
     */
    private Double getFileSizeMegaBytes(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        long sizeInBytes = file.length();
        return sizeInBytes / (1024.0 * 1024.0);
    }

    /**
     * Checks if the image has a 16:9 aspect ratio.
     *
     * @param image the image to check
     * @return true if aspect ratio is 16:9, false otherwise
     */
    private boolean isAspectRatio16by9(Image image) {
        int width = image.getWidth(null);
        int height = image.getHeight(null);
        if (width <= 0 || height <= 0) {
            log.warn("Invalid image dimensions: width={}, height={}", width, height);
            return false;
        }
        // Use cross multiplication to avoid integer division issues
        return width * ASPECT_RATIO_HEIGHT == height * ASPECT_RATIO_WIDTH;
    }

    /**
     * Safely deletes a file, logging any failure.
     *
     * @param file the file to delete
     */
    private void safeDeleteFile(File file) {
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                log.warn("Failed to delete file: {}", file.getAbsolutePath());
            }
        }
    }

    /**
     * Sends an error message via WebSocket.
     *
     * @param message the error message
     */
    private void sendError(String message) {
        messagingTemplate.convertAndSend(TOPIC_ERROR, message);
    }
}