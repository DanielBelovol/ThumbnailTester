package com.example.ThumbnailTester.data.thumbnail;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "thumbnail_stats")
public class ThumbnailStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_id", nullable = false)
    private ThumbnailData thumbnailData;

    private Double ctr; // Click-through rate (CTR)
    private Integer impressions;
    private Integer views;
}
