package com.example.ThumbnailTester.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThumbnailTestConfRequest {
    private String testingType;
    private String testingMode;
    private long testingByTimeMinutes;
    private long testingByMetrics;
    private String criterionOfWinner;
}
