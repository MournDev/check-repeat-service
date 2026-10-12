package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 检测策略集实体：对应 check_policy 表（论文全生命周期 P5）
 *
 * 按学位类型（degree_type）差异化配置检测策略，解决单一全局阈值无法区分
 * 本科/硕士/博士标准的问题。超管端 CRUD 维护；
 * CheckPolicyResolver 按论文 degree_type 命中策略，未命中回落 SimilarityThresholdResolver 既有链路。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_policy")
public class CheckPolicy extends BaseEntity {

    /**
     * 学位类型（undergraduate-本科/master-硕士/doctor-博士，唯一）
     */
    @TableField("degree_type")
    private String degreeType;

    /**
     * 合格阈值（总相似比百分比，如 20.00 表示 20%）
     */
    @TableField("threshold")
    private BigDecimal threshold;

    /**
     * 单篇最大相似比上限（百分比）
     */
    @TableField("max_per_source")
    private BigDecimal maxPerSource;

    /**
     * 每篇论文最多查重次数（可空=沿用全局 admin.check-rule.default-max-count）
     */
    @TableField("max_check_count")
    private Integer maxCheckCount;

    /**
     * 两次查重间隔秒数（可空=沿用全局 admin.check-rule.default-interval）
     */
    @TableField("check_interval")
    private Integer checkInterval;

    /**
     * 是否启用（1-启用 0-停用）
     */
    @TableField("enabled")
    private Integer enabled;
}
