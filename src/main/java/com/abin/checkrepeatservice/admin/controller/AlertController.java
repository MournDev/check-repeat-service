package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.mapper.AlertRecordMapper;
import com.abin.checkrepeatservice.admin.mapper.AlertRuleMapper;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.monitor.service.SystemMonitorService;
import com.abin.checkrepeatservice.pojo.entity.AlertRecord;
import com.abin.checkrepeatservice.pojo.entity.AlertRule;
import com.abin.checkrepeatservice.pojo.entity.SysLoginLog;
import com.abin.checkrepeatservice.user.mapper.SysLoginLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预警管理控制器（基于真实监控指标）。
 *
 * <p>规则落 alert_rule、触发记录落 alert_record；活跃预警在读取时按当前指标评估，
 * 指标恢复后自动置为 RESOLVED。不再返回硬编码的假告警。</p>
 */
@RestController
@RequestMapping("/api/admin/alerts")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class AlertController {

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
     * 获取智能预警配置（数据库无规则时返回内置默认阈值）
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> getAlertConfig() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", true);

            List<Map<String, Object>> rules = new ArrayList<>();
            for (AlertRule rule : loadEnabledRules()) {
                Map<String, Object> item = new HashMap<>();
                item.put("type", rule.getRuleType());
                item.put("threshold", rule.getThreshold());
                item.put("duration", rule.getDuration());
                item.put("severity", rule.getSeverity());
                rules.add(item);
            }
            config.put("rules", rules);
            return Result.success("智能预警配置获取成功", config);
        } catch (Exception e) {
            log.error("获取智能预警配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取智能预警配置失败，系统异常，请稍后重试");
        }
    }

    /**
     * 更新智能预警配置（整体覆盖：未出现在 rules 中的旧规则会被禁用）
     */
    @PutMapping("/config")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateAlertConfig(@RequestBody Map<String, Object> config) {
        try {
            if (config == null) {
                return Result.error(ResultCode.PARAM_ERROR, "预警配置不能为空");
            }
            boolean enabled = !(config.get("enabled") instanceof Boolean b && !b);

            // 先全部禁用，再按入参启用/更新，保证"整体覆盖"语义
            alertRuleMapper.update(null, new LambdaUpdateWrapper<AlertRule>()
                    .set(AlertRule::getEnabled, 0));

            if (enabled && config.get("rules") instanceof List<?> items) {
                for (Object obj : items) {
                    if (!(obj instanceof Map<?, ?> ruleMap)) {
                        continue;
                    }
                    String ruleType = asString(ruleMap.get("type"));
                    if (ruleType == null) {
                        continue;
                    }
                    Double threshold = asDouble(ruleMap.get("threshold"));
                    Integer duration = asInt(ruleMap.get("duration"), DEFAULT_DURATION_SECONDS);
                    String severity = asString(ruleMap.get("severity"));
                    upsertRule(ruleType, threshold, duration,
                            severity == null ? "WARNING" : severity);
                }
            }
            log.info("智能预警配置更新成功: enabled={}", enabled);
            return Result.success("智能预警配置更新成功");
        } catch (Exception e) {
            log.error("更新智能预警配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新智能预警配置失败，系统异常，请稍后重试");
        }
    }

    /**
     * 获取当前活跃预警：先按实时指标评估规则，再返回 ACTIVE 记录
     */
    @GetMapping("/active")
    public Result<List<ActiveAlertVO>> getActiveAlerts() {
        try {
            evaluateRules(loadEnabledRules());

            List<ActiveAlertVO> alerts = new ArrayList<>();
            List<AlertRecord> records = alertRecordMapper.selectList(
                    new LambdaQueryWrapper<AlertRecord>()
                            .eq(AlertRecord::getStatus, STATUS_ACTIVE)
                            .orderByDesc(AlertRecord::getTriggerTime));
            for (AlertRecord record : records) {
                ActiveAlertVO vo = new ActiveAlertVO();
                vo.setId(record.getId() == null ? null : record.getId().toString());
                vo.setLevel(levelOf(record.getSeverity()));
                vo.setTitle(record.getTitle());
                vo.setMessage(record.getMessage());
                vo.setTime(record.getTriggerTime());
                alerts.add(vo);
            }
            return Result.success("当前活跃预警信息获取成功", alerts);
        } catch (Exception e) {
            log.error("获取活跃预警失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取活跃预警失败，系统异常，请稍后重试");
        }
    }

    /**
     * 标记预警为已处理（落库为 RESOLVED）
     */
    @PostMapping("/handle")
    public Result<String> handleAlert(@RequestBody HandleAlertReq req) {
        try {
            Long alertId = parseId(req.getAlertId());
            if (alertId == null) {
                return Result.error(ResultCode.PARAM_ERROR, "预警ID不合法");
            }
            AlertRecord record = alertRecordMapper.selectById(alertId);
            if (record == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "预警记录不存在");
            }
            if (STATUS_RESOLVED.equals(record.getStatus())) {
                return Result.success("预警已处理");
            }
            record.setStatus(STATUS_RESOLVED);
            record.setResolveTime(LocalDateTime.now());
            record.setResolvedBy(currentUsername());
            alertRecordMapper.updateById(record);
            log.info("预警已处理: alertId={}, action={}, operator={}",
                    alertId, req.getAction(), record.getResolvedBy());
            return Result.success("预警处理成功");
        } catch (Exception e) {
            log.error("处理预警失败: alertId={}", req.getAlertId(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "处理预警失败，系统异常，请稍后重试");
        }
    }

    // ==================== 规则与评估 ====================

    /**
     * 启用的规则；数据库无配置时返回内置默认规则（只读、不落库）
     */
    private List<AlertRule> loadEnabledRules() {
        List<AlertRule> rules = alertRuleMapper.selectList(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getEnabled, 1));
        if (!rules.isEmpty()) {
            return rules;
        }
        return defaultRules();
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

    private void upsertRule(String ruleType, Double threshold, Integer duration, String severity) {
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

    /**
     * 按当前真实指标评估规则：触发则新增/刷新 ACTIVE 记录，恢复则自动 RESOLVED
     */
    private void evaluateRules(List<AlertRule> rules) {
        for (AlertRule rule : rules) {
            try {
                Double value = readMetric(rule);
                if (value == null || rule.getThreshold() == null) {
                    continue;
                }
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
            } catch (Exception e) {
                log.warn("预警规则评估失败: type={}, error={}", rule.getRuleType(), e.getMessage());
            }
        }
    }

    private AlertRecord findActive(AlertRule rule) {
        return alertRecordMapper.selectOne(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getRuleName, rule.getRuleName())
                .eq(AlertRecord::getStatus, STATUS_ACTIVE)
                .orderByDesc(AlertRecord::getTriggerTime)
                .last("LIMIT 1"));
    }

    private Double readMetric(AlertRule rule) {
        String type = rule.getRuleType();
        if (type == null) {
            return null;
        }
        switch (type) {
            case "high_cpu_usage": {
                Map<String, Object> cpu = systemMonitorService.getCpuInfo();
                Object system = cpu.get("systemCpuUsage");
                Object process = cpu.get("processCpuUsage");
                return firstNumber(system, process);
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

    private String levelOf(String severity) {
        if (severity == null) {
            return "medium";
        }
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "high";
            case "INFO" -> "low";
            default -> "medium";
        };
    }

    private String currentUsername() {
        try {
            return UserBusinessInfoUtils.getCurrentSysUser().getUsername();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Long parseId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Double asDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer asInt(Object value, Integer defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 活跃预警VO
     */
    @Data
    public static class ActiveAlertVO {
        private String id;
        private String level;
        private String title;
        private String message;
        private LocalDateTime time;
    }

    /**
     * 处理预警请求
     */
    @Data
    public static class HandleAlertReq {
        private String alertId;
        private String action; // resolve/dismiss
        private String remark;
    }
}
