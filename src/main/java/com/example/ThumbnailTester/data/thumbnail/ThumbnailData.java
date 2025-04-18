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

    // Связь с таблицей статистики
    @OneToOne(mappedBy = "thumbnail", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ThumbnailStats stats;

    @OneToOne(mappedBy = "thumbnailData") // если ссылается из конфигурации
    private ThumbnailTestConf testConf;

    // Связь с пользователем, который создал миниатюру
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserData user;

    public ThumbnailData(List<ImageOption> imageOptions, String videoUrl, ThumbnailStats stats, ThumbnailTestConf testConf, UserData user) {
        this.imageOptions = imageOptions;
        this.videoUrl = videoUrl;
        this.stats = stats;
        this.testConf = testConf;
        this.user = user;
    }
}
