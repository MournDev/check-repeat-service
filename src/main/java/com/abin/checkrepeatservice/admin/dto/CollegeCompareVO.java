package com.abin.checkrepeatservice.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 按学院统计 VO（论文全生命周期 P5）：
 * 基于 paper_info.similarity_rate 的学院维度平均相似度与论文数。
 */
@Data
public class CollegeCompareVO {

    /**
     * 学院ID（paper_info.college_id，可为 NULL，NULL 归"未分配"桶）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long collegeId;

    /**
     * 学院名称（college 表关联，college_id 为 NULL 的行在 Java 侧置为"未分配"）
     */
    private String collegeName;

    /**
     * 平均相似度（保留 2 位小数）
     */
    private BigDecimal avgRate;

    /**
     * 论文数
     */
    private Long total;
}
