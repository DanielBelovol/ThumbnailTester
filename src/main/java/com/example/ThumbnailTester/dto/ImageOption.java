package com.example.ThumbnailTester.dto;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Entity
@Table(name = "image_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_base64", nullable = false, columnDefinition = "TEXT")
    private String fileBase64;

    @Column(name = "is_winner")
    private boolean isWinner;

    @OneToOne(mappedBy = "imageOption", cascade = CascadeType.ALL)
    private ThumbnailStats thumbnailStats;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_id")
    private ThumbnailData thumbnail;
}

