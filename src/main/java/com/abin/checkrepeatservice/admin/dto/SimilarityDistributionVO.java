package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;

/**
 * 全校查重结果分布 VO（论文全生命周期 P5）：
 * 基于 check_result.repeat_rate 的十分桶计数（0-10/10-20/.../90-100）。
 *
 * 由 AdminStatReportMapper.selectSimilarityDistribution 的派生子表
 * （先按 CASE 打桶再 GROUP BY，兼容 only_full_group_by）聚合而来，
 * 缺失桶由 AdminStatReportServiceImpl 在 Java 侧补 0，保证十桶完整有序。
 */
@Data
public class SimilarityDistributionVO {

    /**
     * 区间名（如 "0-10"、"90-100"，左闭右开，末桶含 100）
     */
    private String bucket;

    /**
     * 该区间查重结果数
     */
    private Long count;
}
