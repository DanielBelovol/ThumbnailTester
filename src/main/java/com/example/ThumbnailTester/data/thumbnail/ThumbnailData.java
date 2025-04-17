package com.example.ThumbnailTester.data.thumbnail;

import com.example.ThumbnailTester.data.user.UserData;
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

    @ElementCollection
    @CollectionTable(name = "thumbnail_files", joinColumns = @JoinColumn(name = "thumbnail_id"))
    @Column(name = "file_base64", nullable = false)
    private List<String> fileBase64List;

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

    public ThumbnailData(List<String> fileBase64List, String videoUrl, ThumbnailStats stats, ThumbnailTestConf testConf, UserData user) {
        this.fileBase64List = fileBase64List;
        this.videoUrl = videoUrl;
        this.stats = stats;
        this.testConf = testConf;
        this.user = user;
    }
}
