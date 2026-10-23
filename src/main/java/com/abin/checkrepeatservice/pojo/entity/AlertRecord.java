package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 预警记录（对应 alert_record 表）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_record")
public class AlertRecord extends BaseEntity {

    @TableField("rule_id")
    private Long ruleId;

    @TableField("rule_name")
    private String ruleName;

    @TableField("severity")
    private String severity;

    @TableField("title")
    private String title;

    @TableField("message")
    private String message;

    @TableField("metric_value")
    private Double metricValue;

    /** ACTIVE / RESOLVED */
    @TableField("status")
    private String status;

    @TableField("trigger_time")
    private LocalDateTime triggerTime;

    @TableField("resolve_time")
    private LocalDateTime resolveTime;

    @TableField("resolved_by")
    private String resolvedBy;
}
