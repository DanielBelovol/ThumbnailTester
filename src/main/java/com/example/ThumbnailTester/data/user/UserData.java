package com.example.ThumbnailTester.data.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "users")
public class UserData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, name = "google_id")
    private String googleId;

    @Column(nullable = false, name = "refresh_token")
    private String refreshToken;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    public UserData(String googleId, String refreshToken) {
        this.googleId = googleId;
        this.refreshToken = refreshToken;
        this.createdAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "UserData{" +
                "id=" + id +
                ", googleId='" + googleId + '\'' +
                ", refreshToken='" + refreshToken + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
