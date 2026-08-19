package com.abin.checkrepeatservice.teacher.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class TrendComparisonVO {
    private Integer total;
    private Integer currentPeriod;
    private Integer previousPeriod;

    public BigDecimal getTrendPercentage() {
        if (previousPeriod == null || previousPeriod == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf((currentPeriod - previousPeriod) * 100.0 / previousPeriod)
                .setScale(1, RoundingMode.HALF_UP);
    }

    public String getTrendType() {
        BigDecimal percentage = getTrendPercentage();
        if (percentage.compareTo(BigDecimal.ZERO) > 0) {
            return "up";
        } else if (percentage.compareTo(BigDecimal.ZERO) < 0) {
            return "down";
        } else {
            return "neutral";
        }
    }
}
