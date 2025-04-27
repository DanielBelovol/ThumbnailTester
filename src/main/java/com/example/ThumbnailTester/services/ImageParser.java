package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.websocket.WebSocketMessage;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class ImageParser {
    public List<Image> getImagesFromBase64(WebSocketMessage webSocketMessage) {
        List<byte[]> decodedImages = webSocketMessage.getImages().stream()
                .map(base64Image -> Base64.getDecoder().decode(base64Image))
                .collect(Collectors.toList());

        List<Image> images = decodedImages.stream()
                .map(byteArr -> {
                    try {
                        return ImageIO.read(new ByteArrayInputStream(byteArr));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());
        return images;
    }

    public Image getImageFromBase64(String base64) {
        try {
            return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
            throw new RuntimeException("Failed to write decoded Base64 to file", e);
        }

        return file;
    }
}
