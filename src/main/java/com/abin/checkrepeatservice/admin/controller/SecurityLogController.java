package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.vo.LogSecurityVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.SysLoginLog;
import com.abin.checkrepeatservice.user.mapper.SysLoginLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 安全日志控制器
 */
@RestController
@RequestMapping("/api/admin/logs/security")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
@Tag(name = "安全日志", description = "登录安全日志查询")
public class SecurityLogController {

    @Resource
    private SysLoginLogMapper sysLoginLogMapper;

    /**
     * 获取安全日志列表
     */
    @Operation(summary = "获取安全日志列表", description = "分页查询登录日志，可按事件类型与时间范围筛选")
    @GetMapping
    public Result<Page<LogSecurityVO>> getSecurityLogs(
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "事件类型（可选，如 login_failed）")
            @RequestParam(required = false) String eventType,
            @Parameter(description = "开始日期（可选）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（可选）")
            @RequestParam(required = false) String endDate) {
        
        log.info("接收获取安全日志列表请求: page={}, size={}, eventType={}, startDate={}, endDate={}", 
                page, size, eventType, startDate, endDate);
        
        Page<SysLoginLog> logPage = new Page<>(page, size);
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        
        // 事件类型筛选（登录失败等）
        if (eventType != null && !eventType.isEmpty()) {
            if ("login_failed".equals(eventType)) {
                wrapper.eq(SysLoginLog::getLoginResult, 0);
            }
        }
        
        // 时间范围筛选
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(SysLoginLog::getLoginTime, LocalDateTime.parse(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(SysLoginLog::getLoginTime, LocalDateTime.parse(endDate));
        }
        
        // 按登录时间倒序
        wrapper.orderByDesc(SysLoginLog::getLoginTime);
        
        Page<SysLoginLog> resultPage = sysLoginLogMapper.selectPage(logPage, wrapper);
        
        // 转换为安全日志VO
        Page<LogSecurityVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<LogSecurityVO> voList = resultPage.getRecords().stream().map(this::convertToSecurityVO).collect(Collectors.toList());
        voPage.setRecords(voList);
        
        log.info("安全日志列表获取成功，共{}条记录", voList.size());
        return Result.success("安全日志列表获取成功", voPage);
        
    }

    /**
     * 转换登录日志为安全日志VO
     */
    private LogSecurityVO convertToSecurityVO(SysLoginLog loginLog) {
        LogSecurityVO vo = new LogSecurityVO();
        vo.setId(loginLog.getId());
        vo.setTimestamp(loginLog.getLoginTime());
        vo.setUsername(loginLog.getUsername());
        vo.setIpAddress(loginLog.getLoginIp());
        vo.setLocation(loginLog.getLoginLocation());
        vo.setDescription(loginLog.getFailReason() != null ? loginLog.getFailReason() : "登录成功");
        
        // 根据登录结果设置事件类型和风险等级
        if (loginLog.getLoginResult() == 0) {
            vo.setEventType("login_failed");
            vo.setRiskLevel("medium");
        } else {
            vo.setEventType("login_success");
            vo.setRiskLevel("low");
        }
        
        return vo;
    }
}