package com.example.ThumbnailTester.repositories;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ThumbnailRepository extends JpaRepository<ThumbnailData, Long> {

}
