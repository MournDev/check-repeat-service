package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

import java.math.BigDecimal;
// 统计数据DTO
@Data
public class StudentDashboardStatsDTO {
    // 论文统计
    private Integer submittedCount;      // 总提交数
    private Integer pendingCount;        // 待审核数
    private Integer approvedCount;       // 已通过数
    private Integer revisionCount;       // 需修改数
    private Integer failedCount;         // 未通过数

    // 进度统计
    private BigDecimal completionRate;   // 完成度百分比
    private Integer currentProgress;     // 当前进度（1-4）

    // 本周趋势
    private Integer thisWeekSubmitted;   // 本周提交数
    private Integer thisWeekApproved;    // 本周通过数

    // 其他统计
    private BigDecimal avgScore;         // 平均分
    private Integer totalWordCount;      // 总字数
}