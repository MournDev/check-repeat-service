package com.abin.checkrepeatservice.teacher.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendResultDTO {
    private List<String> xAxis;
    private List<ChartSeriesDTO> series;
    private Integer totalReviews;
    private BigDecimal trendPercentage;
    private String trendType; // up, down, neutral
    private String message;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartSeriesDTO {
        private String name;
        private List<Integer> data;
        private String type = "line";
        private String color = "#1890ff";
    }
}