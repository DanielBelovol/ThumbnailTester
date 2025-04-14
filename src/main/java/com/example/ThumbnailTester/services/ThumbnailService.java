package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.repositories.ThumbnailRepository;
import com.example.ThumbnailTester.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ThumbnailService {
    @Autowired
    private ThumbnailRepository thumbnailRepository;
    @Autowired
    private UserRepository userRepository;

    public ThumbnailData getActiveThumbnailByUser(long userId) {
        return thumbnailRepository.findByUserIdAndIsActiveTrue(userId);
    }
}
