package com.example.ThumbnailTester.data.thumbnail;

import com.example.ThumbnailTester.data.user.UserData;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "thumbnails")
@AllArgsConstructor
@NoArgsConstructor
public class ThumbnailData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,name = "file_name")
    private String fileName;

    @Column(nullable = false, name="video_url")
    private String videUrl;

    @Column(nullable = false, name = "is_active")
    private boolean isActive;

    // Связь с таблицей статистики
    @OneToOne(mappedBy = "thumbnail", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ThumbnailStats stats;

    // Связь с пользователем, который создал миниатюру
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserData user;
}
