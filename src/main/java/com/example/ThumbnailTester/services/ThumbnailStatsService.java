package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import com.example.ThumbnailTester.repositories.ThumbnailRepository;
import com.example.ThumbnailTester.repositories.ThumbnailStatsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ThumbnailStatsService {
    @Autowired
    private ThumbnailStatsRepository statsRepository;
    @Autowired
    private ThumbnailRepository thumbnailRepository;

    public ThumbnailStats getByThumbnailId(long thumbnailId) {
        return thumbnailRepository.findById(thumbnailId).get().getStats();
    }

    public void save(ThumbnailStats thumbnailStats) {
        statsRepository.save(thumbnailStats);
    }
}
