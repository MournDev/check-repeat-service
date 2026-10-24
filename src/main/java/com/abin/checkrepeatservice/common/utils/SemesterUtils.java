package com.abin.checkrepeatservice.common.utils;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学期范围解析（Wave 3 导出）。
 *
 * <p>学期标识：{@code YYYY-YYYY+1-1}（第一学期 09-01 ～ 次年 01-31）、
 * {@code YYYY-YYYY+1-2}（第二学期 次年 02-01 ～ 次年 07-31）。
 * 显式传 startDate/endDate 时优先于学期。</p>
 */
public final class SemesterUtils {

    private static final Pattern SEMESTER_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})-(1|2)$");

    private SemesterUtils() {
    }

    public record Range(LocalDateTime start, LocalDateTime endExclusive, String semester) {
    }

    /**
     * 解析查询范围。start 为闭区间下界，endExclusive 为开区间上界（SQL 用 {@code <}）。
     */
    public static Range resolve(String semester, String startDate, String endDate) {
        LocalDateTime start = DateTimeUtils.parseStart(startDate);
        LocalDateTime endExclusive = DateTimeUtils.parseEndExclusive(endDate);

        if (start == null && endExclusive == null && semester != null && !semester.isBlank()) {
            Matcher matcher = SEMESTER_PATTERN.matcher(semester.trim());
            if (!matcher.matches()) {
                throw new BusinessException(ResultCode.PARAM_FORMAT_ERROR,
                        "学期格式应为 YYYY-YYYY-1 或 YYYY-YYYY-2：" + semester, null);
            }
            int firstYear = Integer.parseInt(matcher.group(1));
            int secondYear = Integer.parseInt(matcher.group(2));
            int term = Integer.parseInt(matcher.group(3));
            if (secondYear != firstYear + 1) {
                throw new BusinessException(ResultCode.PARAM_FORMAT_ERROR,
                        "学期年份必须连续，如 2026-2027-1：" + semester, null);
            }
            if (term == 1) {
                start = LocalDate.of(firstYear, 9, 1).atStartOfDay();
                endExclusive = LocalDate.of(secondYear, 2, 1).atStartOfDay();
            } else {
                start = LocalDate.of(secondYear, 2, 1).atStartOfDay();
                endExclusive = LocalDate.of(secondYear, 8, 1).atStartOfDay();
            }
        }

        return new Range(start, endExclusive, semester);
    }
}
