package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 查重规则实体类
 * 定义查重检测的各项规则和阈值
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_rule")
public class CheckRule extends BaseEntity {

    /**
     * 规则名称
     */
    @TableField("rule_name")
    private String ruleName;

    /**
     * 规则编码（唯一）
     */
    @TableField("rule_code")
    private String ruleCode;

    /**
     * 通过阈值（百分比，如80表示80%）
     *
     * @deprecated 不参与合格判定，只是历史遗留列。真正的阈值真值源是
     * {@code similarity_threshold} / {@code category_similarity_threshold} 两张表，
     * 由 {@code SimilarityThresholdResolver} 按「专业 &gt; 学院 &gt; 全局」解析。
     * 新增分支写入的值仅用于回显，编辑分支根本不赋值，故不可作为判定依据。
     * 列本身保留（schema 不动），请勿在新代码里读取。
     */
    @Deprecated
    @TableField("pass_threshold")
    private BigDecimal passThreshold;

    /**
     * 最大重新提交次数
     */
    @TableField("max_re_submit_count")
    private Integer maxReSubmitCount;

    /**
     * 对比库（JSON格式存储对比库ID列表）
     */
    @TableField("compare_lib")
    private String compareLib;

    /**
     * 检测间隔时间（秒）
     */
    @TableField("check_interval")
    private Integer checkInterval;

    /**
     * 最大检测次数
     */
    @TableField("max_check_count")
    private Integer maxCheckCount;

    /**
     * 是否默认规则（0-否，1-是）
     */
    @TableField("is_default")
    private Integer isDefault;

    /**
     * 规则描述
     */
    @TableField("description")
    private String description;

    // 冗余字段 - 便于查询显示
    /**
     * 对比库名称列表
     */
    @TableField(exist = false)
    private String compareLibNames;
}