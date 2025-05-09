package com.example.ThumbnailTester.dto;

import com.example.ThumbnailTester.data.thumbnail.ThumbnailData;
import com.example.ThumbnailTester.data.thumbnail.ThumbnailStats;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "image_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class
ImageOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "text")
    private String text;

    @Column(name = "is_winner")
    private boolean isWinner;

    @OneToOne(mappedBy = "imageOption", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private ThumbnailStats thumbnailStats;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_id")
    @JsonBackReference
    private ThumbnailData thumbnail;
}
