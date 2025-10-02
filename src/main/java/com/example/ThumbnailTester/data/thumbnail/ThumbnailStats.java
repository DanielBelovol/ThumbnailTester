package com.example.ThumbnailTester.data.thumbnail;

import com.example.ThumbnailTester.dto.ImageOption;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "thumbnail_stats")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThumbnailStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_id", nullable = false)
    @JsonBackReference
    private ImageOption imageOption;

    private Integer views;
    private Double ctr;
    private Double averageViewDuration;
    private Double advCtr;
    private Integer comments;
    private Integer shares;
    private Integer likes;
    private Integer subscribersGained;
    private Double averageViewPercentage;
    private Long totalWatchTime;
}
