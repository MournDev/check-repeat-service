package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 能力评估雷达图DTO
 */
@Data
public class AbilityRadarDTO {
    private Integer paperCount;         // 论文数量得分 (0-10)
    private Integer passRate;           // 通过率 (0-100)
    private BigDecimal averageSimilarity; // 平均相似度 (0-30)
    private Integer revisionTimes;      // 修改次数 (0-5)
    private Integer onTimeSubmission;   // 按时提交率 (0-100)
    private Integer advisorRating;      // 导师评分 (0-100)
}