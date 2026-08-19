package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 查重结果实体类：存储每次查重的详细结果信息
 * 对应数据库表：check_result
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("check_result")
public class CheckResult extends BaseEntity {

    /**
     * 查重任务ID（非空，关联check_task.id）
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 论文ID（非空，关联paper_info.id）
     */
    @TableField("paper_id")
    private Long paperId;

    /**
     * 论文提交ID（可选，关联paper_submit.id）
     */
    @TableField("submit_id")
    private Long submitId;

    /**
     * 查重规则ID（可选，关联check_rule.id）
     */
    @TableField("rule_id")
    private Long ruleId;

    /**
     * 重复率（非空，百分比形式，如25.32表示25.32%）
     */
    @TableField("repeat_rate")
    private BigDecimal repeatRate;

    /**
     * 查重来源（非空，LOCAL-本地库，THIRD_PARTY-第三方API）
     */
    @TableField("check_source")
    private String checkSource;

    /**
     * 最相似论文标题（本地库查重时记录）
     */
    @TableField("most_similar_paper")
    private String mostSimilarPaper;

    /**
     * 最相似论文ID（本地库查重时记录，关联paper_library.id）
     */
    @TableField("similar_paper_id")
    private Long similarPaperId;

    /**
     * 查重报告链接（第三方API查重时使用）
     */
    @TableField("report_url")
    private String reportUrl;

    /**
     * 查重完成时间
     */
    @TableField("check_time")
    private LocalDateTime checkTime;

    /**
     * 查重耗时（秒）
     */
    @TableField("check_duration")
    private Integer checkDuration;

    /**
     * 论文总字数
     */
    @TableField("word_count")
    private Integer wordCount;

    /**
     * 重复字数
     */
    @TableField("repeat_word_count")
    private Integer repeatWordCount;

    /**
     * 查重详情（JSON格式，包含重复段落信息）
     */
    @TableField("check_details")
    private String checkDetails;

    /**
     * 状态（非空，0-无效，1-有效）
     */
    @TableField(value = "status", fill = FieldFill.INSERT)
    private Integer status;

}