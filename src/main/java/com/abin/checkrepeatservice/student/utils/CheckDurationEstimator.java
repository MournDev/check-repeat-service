package com.abin.checkrepeatservice.student.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 查重时间预估工具类
 * 
 * 基于字数、系统负载、历史数据的智能预估算法
 */
@Component
@Slf4j
public class CheckDurationEstimator {

    /**
     * 默认配置
     */
    private static final int BASE_TIME_SECONDS = 30;
    private static final int WORD_COUNT_THRESHOLD_1 = 5000;
    private static final int WORD_COUNT_THRESHOLD_2 = 10000;
    private static final int WORD_COUNT_THRESHOLD_3 = 20000;
    
    // 各阶段权重
    private static final double FILE_PARSING_WEIGHT = 0.1;
    private static final double TEXT_COMPARING_WEIGHT = 0.7;
    private static final double REPORT_GENERATING_WEIGHT = 0.2;

    /**
     * 预估查重时长（秒）
     * 
     * @param wordCount 论文字数
     * @return 预估秒数
     */
    public int estimateDuration(Integer wordCount) {
        return estimateDuration(wordCount, 1.0);
    }

    /**
     * 预估查重时长（考虑系统负载）
     * 
     * @param wordCount 论文字数
     * @param loadFactor 系统负载系数 (0.0-1.0)
     * @return 预估秒数
     */
    public int estimateDuration(Integer wordCount, double loadFactor) {
        if (wordCount == null || wordCount == 0) {
            return BASE_TIME_SECONDS;
        }

        // 1. 基础时间计算（分段算法）
        double baseTime = calculateBaseTime(wordCount);

        // 2. 考虑系统负载
        double adjustedTime = baseTime * (1 + loadFactor);

        // 3. 向上取整，最少 30 秒
        return Math.max(BASE_TIME_SECONDS, (int) Math.ceil(adjustedTime));
    }

    /**
     * 分阶段预估时间
     * 
     * @param wordCount 论文字数
     * @return 各阶段时间（秒）
     */
    public StageDuration estimateStageDuration(Integer wordCount) {
        int totalSeconds = estimateDuration(wordCount);

        // 按权重分配各阶段时间
        int fileParsingSeconds = (int) (totalSeconds * FILE_PARSING_WEIGHT);
        int textComparingSeconds = (int) (totalSeconds * TEXT_COMPARING_WEIGHT);
        int reportGeneratingSeconds = (int) (totalSeconds * REPORT_GENERATING_WEIGHT);

        return new StageDuration(
            fileParsingSeconds,
            textComparingSeconds,
            reportGeneratingSeconds,
            totalSeconds
        );
    }

    /**
     * 计算基础时间（分段算法）
     */
    private double calculateBaseTime(Integer wordCount) {
        if (wordCount <= WORD_COUNT_THRESHOLD_1) {
            // 5000 字以下：每 1000 字 5 秒
            return wordCount / 1000.0 * 5;
        } else if (wordCount <= WORD_COUNT_THRESHOLD_2) {
            // 5000-10000 字：基础 25 秒 + 超出部分每 1000 字 8 秒
            return 25 + (wordCount - WORD_COUNT_THRESHOLD_1) / 1000.0 * 8;
        } else if (wordCount <= WORD_COUNT_THRESHOLD_3) {
            // 10000-20000 字：基础 65 秒 + 超出部分每 1000 字 10 秒
            return 65 + (wordCount - WORD_COUNT_THRESHOLD_2) / 1000.0 * 10;
        } else {
            // 20000 字以上：基础 165 秒 + 超出部分每 1000 字 12 秒
            return 165 + (wordCount - WORD_COUNT_THRESHOLD_3) / 1000.0 * 12;
        }
    }

    /**
     * 阶段时长 DTO
     */
    public static class StageDuration {
        
        /**
         * 文件解析阶段（秒）
         */
        private final int fileParsingSeconds;
        
        /**
         * 文本比对阶段（秒）
         */
        private final int textComparingSeconds;
        
        /**
         * 报告生成阶段（秒）
         */
        private final int reportGeneratingSeconds;
        
        /**
         * 总时长（秒）
         */
        private final int totalSeconds;

        public StageDuration(int fileParsingSeconds, int textComparingSeconds, 
                           int reportGeneratingSeconds, int totalSeconds) {
            this.fileParsingSeconds = fileParsingSeconds;
            this.textComparingSeconds = textComparingSeconds;
            this.reportGeneratingSeconds = reportGeneratingSeconds;
            this.totalSeconds = totalSeconds;
        }

        public int getFileParsingSeconds() {
            return fileParsingSeconds;
        }

        public int getTextComparingSeconds() {
            return textComparingSeconds;
        }

        public int getReportGeneratingSeconds() {
            return reportGeneratingSeconds;
        }

        public int getTotalSeconds() {
            return totalSeconds;
        }
    }
}
