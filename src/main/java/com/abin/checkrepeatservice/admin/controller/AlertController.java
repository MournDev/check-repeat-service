package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.common.Result;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预警管理控制器
 */
@RestController
@RequestMapping("/api/admin/alerts")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class AlertController {

    /**
     * 获取智能预警配置
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> getAlertConfig() {
        log.info("接收获取智能预警配置请求");
        
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", true);
            
            List<Map<String, Object>> rules = new ArrayList<>();
            
            // CPU使用率预警规则
            Map<String, Object> cpuRule = new HashMap<>();
            cpuRule.put("type", "high_cpu_usage");
            cpuRule.put("threshold", 80);
            cpuRule.put("duration", 300);
            rules.add(cpuRule);
            
            // 内存使用率预警规则
            Map<String, Object> memoryRule = new HashMap<>();
            memoryRule.put("type", "high_memory_usage");
            memoryRule.put("threshold", 85);
            memoryRule.put("duration", 300);
            rules.add(memoryRule);
            
            // 登录失败次数预警规则
            Map<String, Object> loginRule = new HashMap<>();
            loginRule.put("type", "frequent_login_failures");
            loginRule.put("threshold", 5);
            loginRule.put("duration", 300);
            rules.add(loginRule);
            
            config.put("rules", rules);
            
            log.info("智能预警配置获取成功");
            return Result.success("智能预警配置获取成功", config);
            
        } catch (Exception e) {
            log.error("获取智能预警配置失败: {}", e.getMessage(), e);
            return Result.error(500, "获取智能预警配置失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 更新智能预警配置
     */
    @PutMapping("/config")
    public Result<String> updateAlertConfig(@RequestBody Map<String, Object> config) {
        log.info("接收更新智能预警配置请求: {}", config);
        
        try {
            // 这里应该保存配置到数据库或配置中心
            // 目前只是简单记录日志
            log.info("预警配置已更新: {}", config);
            return Result.success("智能预警配置更新成功");
        } catch (Exception e) {
            log.error("更新智能预警配置失败: {}", e.getMessage(), e);
            return Result.error(500, "更新智能预警配置失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取当前活跃的预警信息
     */
    @GetMapping("/active")
    public Result<List<ActiveAlertVO>> getActiveAlerts() {
        log.info("接收获取当前活跃预警信息请求");
        
        try {
            List<ActiveAlertVO> alerts = new ArrayList<>();
            
            // 模拟一些活跃预警
            ActiveAlertVO alert1 = new ActiveAlertVO();
            alert1.setId("alert_1");
            alert1.setLevel("high");
            alert1.setTitle("CPU使用率过高");
            alert1.setMessage("当前CPU使用率达到85%");
            alert1.setTime(LocalDateTime.now().minusMinutes(5));
            alerts.add(alert1);
            
            ActiveAlertVO alert2 = new ActiveAlertVO();
            alert2.setId("alert_2");
            alert2.setLevel("medium");
            alert2.setTitle("频繁登录失败");
            alert2.setMessage("检测到同一IP地址多次登录失败");
            alert2.setTime(LocalDateTime.now().minusMinutes(15));
            alerts.add(alert2);
            
            log.info("当前活跃预警信息获取成功，共{}条", alerts.size());
            return Result.success("当前活跃预警信息获取成功", alerts);
            
        } catch (Exception e) {
            log.error("获取当前活跃预警信息失败: {}", e.getMessage(), e);
            return Result.error(500, "获取当前活跃预警信息失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 标记预警为已处理
     */
    @PostMapping("/handle")
    public Result<String> handleAlert(@RequestBody HandleAlertReq req) {
        log.info("接收处理预警请求: alertId={}, action={}, remark={}", 
                req.getAlertId(), req.getAction(), req.getRemark());
        
        try {
            // 这里应该更新预警状态到数据库
            // 目前只是简单记录日志
            log.info("预警 {} 已被{}处理: {}", req.getAlertId(), req.getAction(), req.getRemark());
            return Result.success("预警处理成功");
        } catch (Exception e) {
            log.error("处理预警失败: {}", e.getMessage(), e);
            return Result.error(500, "处理预警失败: " + "系统异常，请稍后重试");
        }
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