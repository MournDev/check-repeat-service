package com.abin.checkrepeatservice.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 重复率趋势VO：单次有效查重结果的一个趋势点（论文全生命周期 P5）
 */
@Data
public class SimilarityTrendVO {

    /**
     * 查重任务ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long taskId;

    /**
     * 查重完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkTime;

    /**
     * 重复率/总相似比（百分比）
     */
    private BigDecimal repeatRate;

    /**
     * 单篇最大相似比
     */
    private BigDecimal maxPerSource;

    /**
     * 引用率
     */
    private BigDecimal citationRate;
}
