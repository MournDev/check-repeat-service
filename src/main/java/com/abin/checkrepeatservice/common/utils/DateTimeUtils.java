package com.abin.checkrepeatservice.common.utils;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 时间工具类（格式化 + 请求参数解析）
 */
public class DateTimeUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_SLASH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter DATE_SLASH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 只含日期、不含时间部分的写法（兼容 - 与 / 两种分隔符）。
     *
     * <p>月/日固定两位：下面各 formatter 用的都是 MM/dd，宽度必须精确匹配。
     * 写成 {@code \d{1,2}} 会让人以为 2026-1-3 也支持，实际它会在解析阶段失败。</p>
     */
    private static final Pattern DATE_ONLY = Pattern.compile("^\\d{4}[-/]\\d{2}[-/]\\d{2}$");

    /**
     * 格式化 LocalDateTime 为指定字符串
     * @param dateTime 待格式化的时间
     * @param pattern 格式（如“yyyy-MM-dd HH:mm:ss”）
     * @return 格式化后的字符串
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        if (dateTime == null || pattern == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return dateTime.format(formatter);
    }

    /**
     * 宽松解析前端传来的日期时间字符串。
     * 依次尝试：ISO-8601 → yyyy-MM-dd HH:mm:ss → yyyy-MM-dd → 斜杠写法。
     *
     * <p>空串返回 null，表示「不启用该过滤条件」（调用方据此跳过该条件）。</p>
     *
     * <p>无法解析时抛业务异常（400），而不是静默返回 null——
     * 否则用户日期填错会看到全量数据，看起来像"筛选没生效"。</p>
     */
    public static LocalDateTime parseFlexible(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String value = text.trim();

        // ISO-8601，如 2026-10-03T10:00:00
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它格式
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{
                DATE_TIME_FORMATTER, DATE_TIME_SLASH_FORMATTER}) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试其它格式
            }
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{DATE_FORMATTER, DATE_SLASH_FORMATTER}) {
            try {
                return LocalDate.parse(value, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 继续尝试其它格式
            }
        }

        // 注意 BusinessException 没有 (ResultCode, String) 构造器——
        // 两参写法会绑定到 (ResultCode, Object extraData)，自定义文案会被静默丢弃。
        throw new BusinessException(ResultCode.PARAM_FORMAT_ERROR,
                "日期格式不正确（应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）：" + value, null);
    }

    /**
     * 解析「开始时间」查询条件（含该时刻）。
     */
    public static LocalDateTime parseStart(String text) {
        return parseFlexible(text);
    }

    /**
     * 解析「结束时间」查询条件，返回<b>开区间上界</b>，需配合 SQL 的 {@code <} 比较使用。
     *
     * <p>只给日期（如 2026-10-03）时取<b>次日 0 点</b>，这样 {@code operation_time < end}
     * 恰好覆盖 10-03 一整天。</p>
     *
     * <p>不要退而求其次用 23:59:59.999999999：本项目时间列都是 DATETIME（精度 0），
     * MySQL 会对小数秒四舍五入进位到次日零点，把次日的数据也算进来。</p>
     */
    public static LocalDateTime parseEndExclusive(String text) {
        LocalDateTime parsed = parseFlexible(text);
        if (parsed == null) {
            return null;
        }
        return isDateOnly(text) ? parsed.plusDays(1) : parsed;
    }

    private static boolean isDateOnly(String text) {
        return text != null && DATE_ONLY.matcher(text.trim()).matches();
    }
}
