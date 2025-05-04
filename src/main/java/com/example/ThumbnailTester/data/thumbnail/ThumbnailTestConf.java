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
    private TestConfType testType;

    @Enumerated(EnumType.STRING)
    @Column(name = "testing_type", nullable = false)
    private TestingType testingType;

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


    @Override
    public String toString() {
        return "ThumbnailTestConf{" +
                "id=" + id +
                ", testType=" + testType +
                ", testingType=" + testingType +
                ", testingByTimeMinutes=" + testingByTimeMinutes +
                ", testingByMetrics=" + testingByMetrics +
                ", criterionOfWinner=" + criterionOfWinner +
                ", thumbnailData.id=" + (thumbnailData != null ? thumbnailData.getId() : "null") +
                '}';
    }
}
