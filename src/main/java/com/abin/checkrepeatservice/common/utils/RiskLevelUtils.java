package com.abin.checkrepeatservice.common.utils;

/**
 * 相似度风险等级
 *
 * 分档 15 / 30 / 50，与学生端查重页（StudentCheckWithWebSocket.vue 的 getRiskLevel）一致，
 * 学生看到的和通知里写的不会打架。
 *
 * 注意本工具只覆盖「风险等级」，不要和「合格线」混为一谈：合格与否由
 * admin.check-rule.default-threshold（默认 20.00）判定，落在论文的 checkResult 上，
 * 与这里的四档无关。
 *
 * 已知未收敛的同名逻辑（本次未动，改动会影响既有可见文案）：
 *   - EnhancedPdfReportGenerator.getRiskLevel 用 10/20/30 分档，与这里不同
 *   - 前端另有几处本地计算，且存在「中风险 / 中等风险」两种写法
 */
public class RiskLevelUtils {

    private RiskLevelUtils() {
    }

    /**
     * 按相似度给出风险等级
     *
     * @param similarity 相似度百分比数值，如 12.34
     * @return 低风险 / 中等风险 / 高风险 / 极高风险
     */
    public static String of(double similarity) {
        if (similarity <= 15) return "低风险";
        if (similarity <= 30) return "中等风险";
        if (similarity <= 50) return "高风险";
        return "极高风险";
    }
}
