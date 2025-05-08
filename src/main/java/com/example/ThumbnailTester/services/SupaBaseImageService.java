package com.example.ThumbnailTester.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class SupaBaseImageService {
    private static final Logger log = LoggerFactory.getLogger(SupaBaseImageService.class);
    public File getFileFromPath(URL url){
        log.info("getFileFromPath started with url: " + url);
        String fileName = getUniqueFileName(url);
        File directory = new File(System.getProperty("java.io.tmpdir"), "thumbnails");

        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                log.error("Failed to create directory: " + directory.getAbsolutePath());
            }
        }
        File file = new File(directory, fileName);
        log.info("created file: " + file.getAbsolutePath());
        try(InputStream in = url.openStream(); OutputStream out = new FileOutputStream(file)){
            byte[] buffer = new byte[16384];
            int bytesRead;
            while((bytesRead = in.read(buffer)) != -1){
                out.write(buffer, 0, bytesRead);
            }
            return file;
        }catch (Exception e) {
            log.error("Error downloading file from URL: " + url, e);
        }
        return null;
    }


    public String getFileName(URL url) {
        log.info("get fileName started with url: " + url);
        // Extract filename from URL path
        String path = url.getPath();
        return Paths.get(path).getFileName().toString();
    }

    public void deleteFileWithPath(File file) {
        if (file == null) {
            log.warn("File is null, cannot delete");
            return;
        }
        if (!file.exists()) {
            log.warn("File does not exist: " + file.getAbsolutePath());
            return;
        }
        log.info("trying to delete file: " + file.getAbsolutePath());
        try {
            boolean deleted = file.delete();
            if (deleted) {
                log.info("deleted file: " + file.getAbsolutePath());
            } else {
                log.warn("Failed to delete file: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Error deleting file: " + file.getAbsolutePath(), e);
        }
    }
    public String getUniqueFileName(URL url) {
        String originalFileName = getFileName(url);
        String extension = "";

        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFileName.substring(dotIndex);
            originalFileName = originalFileName.substring(0, dotIndex);
        }

        // generation hash for unique name
        String urlString = url.toString();
        String hash = md5Hex(urlString);

        // creating unique name: originalName + "_" + hash + extension
        return originalFileName + "_" + hash + extension;
    }

    private String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not found", e);
            // fallback
            return input.replaceAll("[^a-zA-Z0-9]", "");
        }
    }
}
