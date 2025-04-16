package com.example.ThumbnailTester.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThumbnailTestConfRequest {
    private String testType;
    private String testingType;
    private long testingByTimeMinutes;
    private long testingByMetrics;
    private String criterionOfWinner;
}
