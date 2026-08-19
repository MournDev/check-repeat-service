package com.abin.checkrepeatservice.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendQueryDTO {
    private Long teacherId;

    @NotBlank(message = "时间范围不能为空")
    private String timeRange; // week, month, quarter, year

    @NotBlank(message = "图表类型不能为空")
    private String chartType; // daily, weekly, monthly

    private LocalDate startDate;
    private LocalDate endDate;

    // 获取时间范围
    public DateRange getDateRange() {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate;

        if (start == null) {
            switch (timeRange.toLowerCase()) {
                case "week":
                    start = end.minusWeeks(1);
                    break;
                case "month":
                    start = end.minusMonths(1);
                    break;
                case "quarter":
                    start = end.minusMonths(3);
                    break;
                case "year":
                    start = end.minusYears(1);
                    break;
                default:
                    start = end.minusWeeks(1);
            }
        }

        return new DateRange(start, end);
    }

    @Data
    @AllArgsConstructor
    public static class DateRange {
        private LocalDate startDate;
        private LocalDate endDate;
    }
}
