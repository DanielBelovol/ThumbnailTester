package com.example.ThumbnailTester;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ThumbnailTesterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThumbnailTesterApplication.class, args);

    }

}
