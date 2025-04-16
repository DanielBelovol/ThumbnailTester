package com.example.ThumbnailTester.DTO;

import com.example.ThumbnailTester.data.thumbnail.CriterionOfWinner;
import com.example.ThumbnailTester.data.thumbnail.TestConfType;
import com.example.ThumbnailTester.data.thumbnail.TestingType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThumbnailTestConfDTO {
    private String testType;
    private String testingType;
    private long testingByTimeMinutes;
    private long testingByMetrics;
    private String criterionOfWinner;
}
