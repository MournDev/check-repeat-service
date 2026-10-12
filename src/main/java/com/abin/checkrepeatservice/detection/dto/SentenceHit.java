package com.abin.checkrepeatservice.detection.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 句级命中 DTO（论文全生命周期 P5）
 * 目标论文正文中被判定为相似的单个句子，含命中来源与相似度。
 */
@Data
public class SentenceHit {

    /**
     * 句子文本（超 1000 字符截断）
     */
    private String sentenceText;

    /**
     * 句子在正文（剔除参考文献区块后）中的起始偏移
     */
    private Integer startIndex;

    /**
     * 句相似度（百分比，0-100，两位小数）
     */
    private BigDecimal similarity;

    /**
     * 是否引用句（句尾带 [n] 引注且文档存在参考文献区块）
     */
    private Boolean isCitation;

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
