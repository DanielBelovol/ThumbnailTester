package com.example.ThumbnailTester.data.thumbnail;

import com.example.ThumbnailTester.data.user.UserData;
import com.example.ThumbnailTester.dto.ImageOption;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Data
@Table(name = "thumbnails")
@AllArgsConstructor
@NoArgsConstructor
public class ThumbnailData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "thumbnail", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ImageOption> imageOptions;

    @Column(nullable = false, name = "video_url")
    private String videoUrl;

    @OneToOne(mappedBy = "thumbnailData")
    private ThumbnailTestConf testConf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserData user;

    public ThumbnailData(List<ImageOption> imageOptions, String videoUrl, ThumbnailTestConf testConf, UserData user) {
        this.imageOptions = imageOptions;
        this.videoUrl = videoUrl;
        this.testConf = testConf;
        this.user = user;
    }
}
