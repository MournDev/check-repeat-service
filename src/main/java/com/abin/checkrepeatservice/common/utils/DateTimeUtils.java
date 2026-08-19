package com.abin.checkrepeatservice.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具类（格式化 LocalDateTime）
 */
public class DateTimeUtils {
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
}
