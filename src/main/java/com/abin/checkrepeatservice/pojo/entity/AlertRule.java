package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预警规则（对应 alert_rule 表）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_rule")
public class AlertRule extends BaseEntity {

    @TableField("rule_name")
    private String ruleName;

    /** 规则类型：high_cpu_usage / high_memory_usage / high_disk_usage / frequent_login_failures */
    @TableField("rule_type")
    private String ruleType;

    @TableField("metric_name")
    private String metricName;

    @TableField("threshold")
    private Double threshold;

    /** 统计窗口（秒），登录失败类规则使用 */
    @TableField("duration")
    private Integer duration;

    @TableField("severity")
    private String severity;

    @TableField("enabled")
    private Integer enabled;

    @TableField("notify_email")
    private String notifyEmail;

    @TableField("description")
    private String description;
}
