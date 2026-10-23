package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.mapper.AlertRecordMapper;
import com.abin.checkrepeatservice.admin.mapper.AlertRuleMapper;
import com.abin.checkrepeatservice.monitor.service.SystemMonitorService;
import com.abin.checkrepeatservice.pojo.entity.AlertRecord;
import com.abin.checkrepeatservice.pojo.entity.AlertRule;
import com.abin.checkrepeatservice.pojo.entity.SysLoginLog;
import com.abin.checkrepeatservice.user.mapper.SysLoginLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能预警规则评估服务。
 *
 * <p>规则落 alert_rule、触发记录落 alert_record。评估按当前真实监控指标进行：
 * 超过阈值则新增/刷新 ACTIVE 记录，指标恢复则自动置为 RESOLVED。</p>
 *
 * <p>此前评估只挂在「获取活跃预警」接口上，无人打开预警页就不会产生任何记录；
 * 这里改为定时评估（每 5 分钟），页面读取仍会主动评估一次作为兜底。</p>
 */
@Slf4j
@Service
public class AlertEvaluationService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final int DEFAULT_DURATION_SECONDS = 300;

    @Resource
    private AlertRuleMapper alertRuleMapper;

    @Resource
    private AlertRecordMapper alertRecordMapper;

    @Resource
    private SystemMonitorService systemMonitorService;

    @Resource
    private SysLoginLogMapper sysLoginLogMapper;

    /**
     * 定时评估启用的规则（固定延迟 5 分钟，首次启动 60 秒后执行）
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void scheduledEvaluate() {
        evaluateActiveRules();
    }

    /**
     * 按当前真实指标评估所有启用规则。
     *
     * @return 本次实际评估的规则数（指标不可读的规则跳过）
     */
    public int evaluateActiveRules() {
        List<AlertRule> rules = loadEnabledRules();
        int evaluated = 0;
        for (AlertRule rule : rules) {
            try {
                Double value = readMetric(rule);
                if (value == null || rule.getThreshold() == null) {
                    continue;
                }
                evaluated++;
                applyEvaluation(rule, value);
            } catch (Exception e) {
                log.warn("预警规则评估失败: type={}, error={}", rule.getRuleType(), e.getMessage());
            }
        }
        return evaluated;
    }

    /**
     * 启用的规则；数据库无配置时返回内置默认规则（只读、不落库）
     */
    public List<AlertRule> loadEnabledRules() {
        List<AlertRule> rules = alertRuleMapper.selectList(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getEnabled, 1));
        if (!rules.isEmpty()) {
            return rules;
        }
        return defaultRules();
    }

    /**
     * 按规则类型插入或更新一条规则（规则配置页的整体覆盖语义）
     */
    public void upsertRule(String ruleType, Double threshold, Integer duration, String severity) {
        AlertRule existing = alertRuleMapper.selectOne(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getRuleType, ruleType)
                .orderByDesc(AlertRule::getId)
                .last("LIMIT 1"));
        AlertRule target = existing == null ? new AlertRule() : existing;
        target.setRuleType(ruleType);
        target.setRuleName(ruleNameOf(ruleType));
        target.setMetricName(metricNameOf(ruleType));
        target.setThreshold(threshold == null ? defaultThresholdOf(ruleType) : threshold);
        target.setDuration(duration == null ? DEFAULT_DURATION_SECONDS : duration);
        target.setSeverity(severity);
        target.setEnabled(1);
        if (existing == null) {
            alertRuleMapper.insert(target);
        } else {
            alertRuleMapper.updateById(target);
        }
    }

    private void applyEvaluation(AlertRule rule, double value) {
        boolean breach = value >= rule.getThreshold();
        AlertRecord active = findActive(rule);
        if (breach) {
            if (active == null) {
                AlertRecord record = new AlertRecord();
                record.setRuleId(rule.getId());
                record.setRuleName(rule.getRuleName());
                record.setSeverity(rule.getSeverity() == null ? "WARNING" : rule.getSeverity());
                record.setTitle(titleOf(rule.getRuleType()));
                record.setMessage(buildMessage(rule, value));
                record.setMetricValue(value);
                record.setStatus(STATUS_ACTIVE);
                record.setTriggerTime(LocalDateTime.now());
                alertRecordMapper.insert(record);
            } else {
                active.setRuleId(rule.getId());
                active.setSeverity(rule.getSeverity() == null ? "WARNING" : rule.getSeverity());
                active.setMetricValue(value);
                active.setMessage(buildMessage(rule, value));
                alertRecordMapper.updateById(active);
            }
        } else if (active != null) {
            active.setStatus(STATUS_RESOLVED);
            active.setResolveTime(LocalDateTime.now());
            active.setResolvedBy("SYSTEM");
            alertRecordMapper.updateById(active);
        }
    }

    private AlertRecord findActive(AlertRule rule) {
        return alertRecordMapper.selectOne(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getRuleName, rule.getRuleName())
                .eq(AlertRecord::getStatus, STATUS_ACTIVE)
                .orderByDesc(AlertRecord::getTriggerTime)
                .last("LIMIT 1"));
    }

    private List<AlertRule> defaultRules() {
        return List.of(
                buildRule("CPU 使用率预警", "high_cpu_usage", "systemCpuUsage", 80.0, 300, "WARNING"),
                buildRule("内存使用率预警", "high_memory_usage", "heapUsagePercent", 85.0, 300, "WARNING"),
                buildRule("磁盘使用率预警", "high_disk_usage", "diskUsage", 90.0, 300, "WARNING"),
                buildRule("登录失败预警", "frequent_login_failures", "loginFailCount", 5.0, 300, "WARNING"));
    }

    private AlertRule buildRule(String name, String type, String metric,
                                Double threshold, Integer duration, String severity) {
        AlertRule rule = new AlertRule();
        rule.setRuleName(name);
        rule.setRuleType(type);
        rule.setMetricName(metric);
        rule.setThreshold(threshold);
        rule.setDuration(duration);
        rule.setSeverity(severity);
        rule.setEnabled(1);
        return rule;
    }

    private Double readMetric(AlertRule rule) {
        String type = rule.getRuleType();
        if (type == null) {
            return null;
        }
        switch (type) {
            case "high_cpu_usage": {
                java.util.Map<String, Object> cpu = systemMonitorService.getCpuInfo();
                return firstNumber(cpu.get("systemCpuUsage"), cpu.get("processCpuUsage"));
            }
            case "high_memory_usage":
                return firstNumber(systemMonitorService.getMemoryInfo().get("heapUsagePercent"));
            case "high_disk_usage":
                return firstNumber(systemMonitorService.getDiskInfo().get("usage"));
            case "frequent_login_failures": {
                int seconds = rule.getDuration() == null || rule.getDuration() <= 0
                        ? DEFAULT_DURATION_SECONDS : rule.getDuration();
                Long count = sysLoginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
                        .eq(SysLoginLog::getLoginResult, 0)
                        .ge(SysLoginLog::getLoginTime, LocalDateTime.now().minusSeconds(seconds)));
                return count == null ? 0.0 : count.doubleValue();
            }
            default:
                return null;
        }
    }

    private Double firstNumber(Object... values) {
        for (Object value : values) {
            if (value instanceof Number n) {
                return n.doubleValue();
            }
        }
        return null;
    }

    private String buildMessage(AlertRule rule, double value) {
        double threshold = rule.getThreshold() == null ? 0 : rule.getThreshold();
        return switch (rule.getRuleType()) {
            case "high_cpu_usage" -> String.format("当前 CPU 使用率 %.1f%%，阈值 %.1f%%", value, threshold);
            case "high_memory_usage" -> String.format("当前内存使用率 %.1f%%，阈值 %.1f%%", value, threshold);
            case "high_disk_usage" -> String.format("当前磁盘使用率 %.1f%%，阈值 %.1f%%", value, threshold);
            case "frequent_login_failures" -> String.format("统计窗口内登录失败 %.0f 次，阈值 %.0f 次",
                    value, threshold);
            default -> String.format("指标当前值 %.1f，阈值 %.1f", value, threshold);
        };
    }

    private String titleOf(String ruleType) {
        return switch (ruleType == null ? "" : ruleType) {
            case "high_cpu_usage" -> "CPU 使用率过高";
            case "high_memory_usage" -> "内存使用率过高";
            case "high_disk_usage" -> "磁盘使用率过高";
            case "frequent_login_failures" -> "登录失败次数过多";
            default -> "系统指标异常";
        };
    }

    private String ruleNameOf(String ruleType) {
        return titleOf(ruleType);
    }

    private String metricNameOf(String ruleType) {
        return switch (ruleType == null ? "" : ruleType) {
            case "high_cpu_usage" -> "systemCpuUsage";
            case "high_memory_usage" -> "heapUsagePercent";
            case "high_disk_usage" -> "diskUsage";
            case "frequent_login_failures" -> "loginFailCount";
            default -> ruleType;
        };
    }

    private double defaultThresholdOf(String ruleType) {
        return switch (ruleType == null ? "" : ruleType) {
            case "high_cpu_usage" -> 80.0;
            case "high_memory_usage" -> 85.0;
            case "high_disk_usage" -> 90.0;
            case "frequent_login_failures" -> 5.0;
            default -> 80.0;
        };
    }
}
