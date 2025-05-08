package com.example.ThumbnailTester.services;
import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.stereotype.Service;
import java.io.File; import java.io.FileOutputStream; import java.io.InputStream; import java.io.OutputStream; import java.net.URL; import java.nio.file.Paths; import java.security.MessageDigest; import java.security.NoSuchAlgorithmException;
@Service public class SupaBaseImageService { private static final Logger log = LoggerFactory.getLogger(SupaBaseImageService.class);
    private static final String THUMBNAILS_DIR_NAME = "thumbnails";
    private static final int BUFFER_SIZE = 16 * 1024; // 16 KB
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;

    /**
     * Downloads a file from the given URL and saves it to a unique file in the temp thumbnails directory.
     * Retries download up to MAX_DOWNLOAD_ATTEMPTS times if incomplete or error occurs.
     *
     * @param url the URL to download from
     * @return the downloaded File or null if failed
     */
    public File getFileFromPath(URL url) {
        log.info("getFileFromPath started with url: {}", url);
        String fileName = getUniqueFileName(url);
        File directory = new File(System.getProperty("java.io.tmpdir"), THUMBNAILS_DIR_NAME);

        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                log.error("Failed to create directory: {}", directory.getAbsolutePath());
            }
        }

        File file = new File(directory, fileName);

        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            log.info("Download attempt {}/{} for file: {}", attempt, MAX_DOWNLOAD_ATTEMPTS, file.getAbsolutePath());
            try (InputStream in = url.openStream(); OutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();

                // Check if file size is reasonable (non-zero)
                if (file.length() > 0) {
                    log.info("Download succeeded on attempt {} with file size: {} bytes", attempt, file.length());
                    return file;
                } else {
                    log.warn("Downloaded file size is zero, retrying...");
                }
            } catch (Exception e) {
                log.error("Error downloading file from URL: {} on attempt {}", url, attempt, e);
            }

            // Delete incomplete or zero-length file before retrying
            if (file.exists() && !file.delete()) {
                log.warn("Failed to delete incomplete file: {}", file.getAbsolutePath());
            }
        }

        log.error("Failed to download file after {} attempts: {}", MAX_DOWNLOAD_ATTEMPTS, file.getAbsolutePath());
        return null;
    }

    /**
     * Extracts the filename from the URL path.
     *
     * @param url the URL
     * @return the filename string
     */
    public String getFileName(URL url) {
        log.info("getFileName started with url: {}", url);
        String path = url.getPath();
        return Paths.get(path).getFileName().toString();
    }

    /**
     * Deletes the file if it exists.
     *
     * @param file the file to delete
     */
    public void deleteFileWithPath(File file) {
        if (file == null) {
            log.warn("File is null, cannot delete");
            return;
        }
        if (!file.exists()) {
            log.warn("File does not exist: {}", file.getAbsolutePath());
            return;
        }
        log.info("Trying to delete file: {}", file.getAbsolutePath());
        try {
            boolean deleted = file.delete();
            if (deleted) {
                log.info("Deleted file: {}", file.getAbsolutePath());
            } else {
                log.warn("Failed to delete file: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Error deleting file: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * Generates a unique filename based on the URL by appending an MD5 hash.
     *
     * @param url the URL
     * @return unique filename string
     */
    public String getUniqueFileName(URL url) {
        String originalFileName = getFileName(url);
        String extension = "";

        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFileName.substring(dotIndex);
            originalFileName = originalFileName.substring(0, dotIndex);
        }

        String urlString = url.toString();
        String hash = md5Hex(urlString);

        return originalFileName + "_" + hash + extension;
    }

    /**
     * Computes MD5 hash of the input string and returns it as hex.
     * Falls back to sanitized input string if MD5 is not available.
     *
     * @param input the input string
     * @return MD5 hex string or sanitized input
     */
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
            return input.replaceAll("[^a-zA-Z0-9]", "");
        }
    }
}