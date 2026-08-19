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
     * 变更前状态（非空，如0-草稿）
     */
    @NotNull(message = "变更前状态不能为空")
    @TableField("old_status")
    private Integer oldStatus;

    /**
     * 变更后状态（非空，如1-已提交）
     */
    @NotNull(message = "变更后状态不能为空")
    @TableField("new_status")
    private Integer newStatus;

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
