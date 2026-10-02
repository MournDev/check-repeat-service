package com.abin.checkrepeatservice.admin.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理员报告统计VO
 * 口径与 /api/admin/reports/list 完全一致：接收同一组筛选参数，对筛选后的全集做聚合
 */
@Data
public class AdminReportStatsVO {

    /**
     * 报告总数
     */
    private Long totalReports;

    /**
     * 已完成数（check_task.check_status = 'completed'）
     */
    private Long completedReports;

    /**
     * 平均相似度（百分比，保留两位小数）
     */
    private BigDecimal averageSimilarity;

    /**
     * 高风险报告数（相似度 > 30%）
     */
    private Long highRiskReports;
}
