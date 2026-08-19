package com.abin.checkrepeatservice.common.utils;

import com.abin.checkrepeatservice.teacher.dto.TrendQueryDTO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class DateUtil {

    /**
     * 获取前一个周期的时间范围
     */
    public static TrendQueryDTO.DateRange getPreviousRange(TrendQueryDTO.DateRange currentRange) {
        long daysBetween = ChronoUnit.DAYS.between(currentRange.getStartDate(),
                currentRange.getEndDate());
        LocalDate previousEnd = currentRange.getStartDate().minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(daysBetween);

        return new TrendQueryDTO.DateRange(previousStart, previousEnd);
    }

    /**
     * 生成连续的月份标签
     */
    public static List<String> generateMonthLabels(LocalDate start, LocalDate end) {
        List<String> labels = new ArrayList<>();
        LocalDate current = start.withDayOfMonth(1);
        LocalDate endMonth = end.withDayOfMonth(1);

        while (!current.isAfter(endMonth)) {
            labels.add(current.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            current = current.plusMonths(1);
        }

        return labels;
    }
}
