package com.example.ThumbnailTester.repositories;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThumbnailRepository extends JpaRepository<ThumbnailData, Long> {
    ThumbnailData getThumbnailByUserId(long userId);

    ThumbnailData findByUserIdAndIsActiveTrue(long userId);

    List<ThumbnailData> findAllByUserId(long userID);

    ThumbnailData findByVideoUrl(String videoUrl);

}
