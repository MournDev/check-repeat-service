package com.abin.checkrepeatservice.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 检测明细-句级条目VO：一条命中句的完整信息（论文全生命周期 P5）
 */
@Data
public class SentenceDetailItemVO {

    /**
     * 句子在正文（剔除参考文献区块后）中的序号，从 0 开始
     */
    private Integer sentenceIndex;

    /**
     * 句子文本
     */
    private String sentenceText;

    /**
     * 句相似度（百分比，如 82.35 表示 82.35%）
     */
    private BigDecimal similarity;

    /**
     * 是否引用句（1-是，引用命中不计入总相似比）
     */
    private Integer isCitation;

    /**
     * 句子在正文中的起始偏移
     */
    private Integer startOffset;

    /**
     * 命中来源论文ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hitPaperId;

    /**
     * 命中来源论文标题
     */
    private String hitPaperTitle;
}
