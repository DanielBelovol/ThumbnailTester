package com.example.ThumbnailTester.data.thumbnail;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "thumbnail_test_config")
public class ThumbnailTestConf {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false)
    private TestingType testType;

    @Enumerated(EnumType.STRING)
    @Column(name = "testing_mode", nullable = false)
    private TestingMode testingMode;

    @Column(name = "testing_by_time_minutes")
    private long testingByTimeMinutes;

    @Column(name = "testing_by_metrics")
    private long testingByMetrics;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterion_of_winner", nullable = false)
    private CriterionOfWinner criterionOfWinner;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "thumbnail_id")
    private ThumbnailData thumbnailData;

    public void setThumbnailData(ThumbnailData thumbnailData) {
        this.thumbnailData = thumbnailData;
        if (thumbnailData != null && thumbnailData.getTestConf() != this) {
            thumbnailData.setTestConf(this);
        }
    }

    @Override
    public String toString() {
        return "ThumbnailTestConf{" +
                "id=" + id +
                ", testType=" + testType +
                ", testingType=" + testingMode +
                ", testingByTimeMinutes=" + testingByTimeMinutes +
                ", testingByMetrics=" + testingByMetrics +
                ", criterionOfWinner=" + criterionOfWinner +
                ", thumbnailData.id=" + (thumbnailData != null ? thumbnailData.getId() : "null") +
                '}';
    }
}
