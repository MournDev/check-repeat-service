package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.mapper.AlertRecordMapper;
import com.abin.checkrepeatservice.admin.mapper.AlertRuleMapper;
import com.abin.checkrepeatservice.admin.service.AlertEvaluationService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.AlertRecord;
import com.abin.checkrepeatservice.pojo.entity.AlertRule;
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
    private AlertEvaluationService alertEvaluationService;

    /**
     * 获取智能预警配置（数据库无规则时返回内置默认阈值）
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> getAlertConfig() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", true);

            List<Map<String, Object>> rules = new ArrayList<>();
            for (AlertRule rule : alertEvaluationService.loadEnabledRules()) {
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
                    alertEvaluationService.upsertRule(ruleType, threshold, duration,
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
            // 页面读取时主动评估一次（定时任务每 5 分钟已评估，这里作为兜底）
            alertEvaluationService.evaluateActiveRules();

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
