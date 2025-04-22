package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.repositories.ThumbnailRepository;
import com.example.ThumbnailTester.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

@Service
public class ThumbnailService {
    @Autowired
    private ThumbnailRepository thumbnailRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ImageParser imageParser;

    public void save(ThumbnailData thumbnailData) {
        thumbnailRepository.save(thumbnailData);
    }

    public boolean isValid(String fileBase64) throws IOException {
        // Decode the base64 string to get the Image
        Image image = imageParser.getImageFromBase64(fileBase64);

        // Create a temporary file to save the image
        int number = new Random().nextInt(1000);
        File tempFile = createTempFileFromImage(image, "thumbnail"+number+".png");

        // Get the file size in MB
        Double size = getFileSizeMegaBytes(tempFile);

        // Check if the file size is >= 2MB and if the aspect ratio is 16:9
        boolean valid = size >= 2 && is16by9(image);

        // Optionally delete the temporary file after validation
        tempFile.delete();

        return valid;
    }

    private File createTempFileFromImage(Image image, String formatName) throws IOException {
        // Convert Image to BufferedImage
        BufferedImage bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the Image into the BufferedImage
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        // Create a temporary file
        File tempFile = File.createTempFile("thumbnail_", "." + formatName);
        ImageIO.write(bufferedImage, formatName, tempFile);

        return tempFile;
    }

    private static Double getFileSizeMegaBytes(File file) {
        return (double) file.length() / (1024 * 1024);
    }

    public static boolean is16by9(Image image) {
        // Get the width and height of the image
        int width = image.getWidth(null);
        int height = image.getHeight(null);

        // Calculate the aspect ratio
        double aspectRatio = (double) width / height;

        // Compare the aspect ratio with 16:9
        return Math.abs(aspectRatio - (16.0 / 9.0)) < 0.01;
    }
}
