package com.abin.checkrepeatservice.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 检测明细页结果VO：任务指标 + 句级明细 + 来源聚合列表（论文全生命周期 P5）
 */
@Data
public class SentenceDetailResultVO {

    /**
     * 查重任务ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long taskId;

    /**
     * 总相似比/重复率（如 15.32 表示 15.32%）
     */
    private BigDecimal checkRate;

    /**
     * 单篇最大相似比
     */
    private BigDecimal maxPerSource;

    /**
     * 引用率（引用命中不计入总相似比）
     */
    private BigDecimal citationRate;

    /**
     * 命中句总数
     */
    private Integer hitCount;

    /**
     * 句级明细（全量，按句序号升序）
     */
    private List<SentenceDetailItemVO> items;

    /**
     * 来源聚合列表（按命中来源论文分组）
     */
    private List<SentenceDetailVO> sources;
}
