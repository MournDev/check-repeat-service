package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学院月度查重额度实体：对应 check_college_quota 表（论文全生命周期 P5）
 *
 * 管理端按学院+月份分配查重额度，查重任务创建时 CollegeQuotaService.checkAndConsume
 * 校验并扣减；唯一键 (college_id, quota_month)，跨月自然重置无需清零任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_college_quota")
public class CheckCollegeQuota extends BaseEntity {

    /**
     * 学院ID（关联 college.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("college_id")
    private Long collegeId;

    /**
     * 额度月份（格式 yyyy-MM，如 2026-10）
     */
    @TableField("quota_month")
    private String quotaMonth;

    /**
     * 当月总额度（次）
     */
    @TableField("total_quota")
    private Integer totalQuota;

    /**
     * 当月已用额度（次）
     */
    @TableField("used_quota")
    private Integer usedQuota;
}
