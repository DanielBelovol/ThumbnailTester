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

@Service
public class SupaBaseImageService {
    private static final Logger log = LoggerFactory.getLogger(SupaBaseImageService.class);
    public File getFileFromPath(URL url){
        log.info("getFileFromPath started with url: " + url);
        String fileName = getFileName(url);
        File directory = new File("src/main/resources/thumbnails");
        if(!directory.exists()){
            directory.mkdir();
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
        }catch (Exception e){
            log.error(e.getMessage());
        }
        return null;
    }

    public String getFileName(URL url) {
        log.info("get fileName started with url: " + url);
        // Example: https://.../user123/my_thumbnail.jpg -> my_thumbnail.jpg
        String path = url.getPath();
        return Paths.get(path).getFileName().toString();
    }
    public void deleteFileWithPath(File file){
        log.info("trying to delete file: " + file.getAbsolutePath());
        try{
        file.delete();
        }catch (Exception e){
            log.error(e.getMessage());
            return;
        }
        log.info("deleted file: " + file.getAbsolutePath());
    }
}
