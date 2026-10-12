package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 查重月度趋势 VO（论文全生命周期 P5）：
 * 基于 check_result.check_time 按月聚合计数与平均重复率，取最近 12 个月。
 */
@Data
public class MonthlyTrendVO {

    /**
     * 月份（yyyy-MM）
     */
    private String ym;

    /**
     * 当月查重次数
     */
    private Long count;

    /**
     * 当月平均重复率（保留 2 位小数）
     */
    private BigDecimal avgRate;
}
