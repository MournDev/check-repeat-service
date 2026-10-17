package com.abin.checkrepeatservice.common.utils;

import com.alibaba.fastjson2.JSON;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报告验真哈希工具（论文全生命周期 P5）
 *
 * 对查重报告关键要素（任务/论文/编号/三项指标）拼 JSON 再加盐做 SHA-256：
 * - 不用 PDF 字节：PDF 生成含时间戳等不可复现元素，字节级哈希无法验真；
 * - 盐来自环境变量 REPORT_HASH_SALT（默认兜底值），防止仅凭公开要素自行伪造哈希；
 * - BigDecimal 统一 toPlainString，避免 20.00 与 20.0 序列化差异导致验真误判。
 *
 * 写入方：CheckTaskEventListener.createCheckReport；
 * 校验方：ReportVerifyService（重算比对）。
 */
public final class ReportHashUtils {

    private ReportHashUtils() {
    }

    /**
     * 构建参与哈希的关键要素 JSON（键序固定 LinkedHashMap，保证两端字节一致；
     * 盐参与序列化，防止仅凭公开要素伪造哈希）
     */
    public static String buildHashSource(Long taskId, String reportNo, BigDecimal checkRate,
                                         BigDecimal maxPerSource, BigDecimal citationRate, Long paperId,
                                         String salt) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("taskId", taskId);
        source.put("reportNo", reportNo);
        source.put("checkRate", plain(checkRate));
        source.put("maxPerSource", plain(maxPerSource));
        source.put("citationRate", plain(citationRate));
        source.put("paperId", paperId);
        source.put("salt", salt);
        return JSON.toJSONString(source);
    }

    /**
     * SHA-256 摘要（小写 hex，64 位）
     */
    public static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
