package com.abin.checkrepeatservice.teacher.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendDataVO {
    // 按天统计
    private String date;
    private Integer reviewCount;
    private Integer approvedCount;
    private Integer rejectedCount;
    private BigDecimal avgReviewTime;

    // 按周统计
    private String yearWeek;
    private String weekLabel;
    private Integer uniquePapers;

    // 按月统计
    private String month;
    private BigDecimal avgSimilarity;
}
