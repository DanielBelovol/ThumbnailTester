package com.example.ThumbnailTester.repositories;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ThumbnailStatsRepository extends JpaRepository<ThumbnailStats, Long> {

}
