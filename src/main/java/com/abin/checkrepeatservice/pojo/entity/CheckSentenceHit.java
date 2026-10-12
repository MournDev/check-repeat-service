package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 句级标红明细实体：对应 check_sentence_hit 表（论文全生命周期 P5）
 *
 * 每次查重任务对正文逐句与候选论文（整篇海明预筛通过者）做句级比对，
 * 命中（句相似度 ≥ check.sentence.threshold）的句子逐条落库，
 * 学生端检测明细页与来源聚合列表的数据源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_sentence_hit")
public class CheckSentenceHit extends BaseEntity {

    /**
     * 查重任务ID（关联 check_task.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("task_id")
    private Long taskId;

    /**
     * 句子在正文（剔除参考文献区块后）中的序号，从 0 开始
     */
    @TableField("sentence_index")
    private Integer sentenceIndex;

    /**
     * 句子文本（超 1000 字符截断）
     */
    @TableField("sentence_text")
    private String sentenceText;

    /**
     * 句子在正文中的起始偏移
     */
    @TableField("start_offset")
    private Integer startOffset;

    /**
     * 句相似度（百分比，如 82.35 表示 82.35%）
     */
    @TableField("similarity")
    private BigDecimal similarity;

    /**
     * 是否引用句（1-句尾带 [n] 引注且文档存在参考文献区块；引用命中不计入总相似比）
     */
    @TableField("is_citation")
    private Integer isCitation;

    /**
     * 命中来源论文ID（关联 paper_info.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("hit_paper_id")
    private Long hitPaperId;

    /**
     * 命中来源论文标题（冗余，便于明细页直接展示）
     */
    @TableField("hit_paper_title")
    private String hitPaperTitle;
}
