package com.abin.checkrepeatservice.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 检测明细-来源聚合VO：单个命中来源论文的句级统计（论文全生命周期 P5）
 */
@Data
public class SentenceDetailVO {

    /**
     * 命中来源论文ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hitPaperId;

    /**
     * 命中来源论文标题
     */
    private String hitPaperTitle;

    /**
     * 命中句数
     */
    private Integer sentenceCount;

    /**
     * 其中引用句（句尾带 [n] 引注）命中数
     */
    private Integer citationSentenceCount;

    /**
     * 组内句相似度平均值（保留 2 位）
     */
    private BigDecimal avgSimilarity;

    /**
     * 该来源命中句去空白字符数占全部命中句去空白字符数的百分比（保留 2 位）
     */
    private BigDecimal sharePercent;
}
