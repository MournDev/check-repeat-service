package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.Length;

/**
 * 论文状态变更日志实体类（对应paper_status_log表）
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("paper_status_log")
public class PaperStatusLog extends BaseEntity {

    /**
     * 论文提交ID（关联paper_submit.id，非空）
     */
    @NotNull(message = "论文ID不能为空")
    @Positive(message = "论文ID必须为正整数")
    @TableField("paper_id")
    private Long paperId;

    /**
     * 变更前状态（对应 paper_status 字典值，如 auditing；库列为 varchar(50)）。
     *
     * ⚠️ 历史踩坑：此字段曾声明为 Integer，而 DictConstants.PaperStatus 的状态值是
     * "auditing" 这类字符串，Integer.parseInt 直接抛 NumberFormatException 且被
     * try/catch 吞掉——状态日志静默写不进去，学生端时间线无数据（P1 已修复）。
     */
    @TableField("old_status")
    private String oldStatus;

    /**
     * 变更后状态（对应 paper_status 字典值，如 completed；库列为 varchar(50)）
     */
    @TableField("new_status")
    private String newStatus;

    /**
     * 状态变更原因（非空，最大500字符）
     */
    @NotBlank(message = "状态变更原因不能为空")
    @Length(max = 500, message = "状态变更原因长度不能超过500字符")
    @TableField("status_reason")
    private String statusReason;

    /**
     * 操作IP地址（可选，最大50字符）
     */
    @Length(max = 50, message = "操作IP地址长度不能超过50字符")
    @TableField("operate_ip")
    private String operateIp;
}
