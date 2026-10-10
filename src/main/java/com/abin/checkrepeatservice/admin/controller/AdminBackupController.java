package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.Impl.SysBackupServiceImpl;
import com.abin.checkrepeatservice.admin.service.SysBackupService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SysBackupLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据库备份管理控制器
 *
 * <p>供「日志中心 - 备份记录」页与「系统配置 - 备份设置」使用。</p>
 *
 * <p>职责分离（P1）：备份属基础设施级操作 —— 手动触发会瞬间吃满 DB I/O、改设置
 * 能直接关掉全站数据兜底，故写操作（触发/改设置）收归超级管理员；历史/状态/
 * 设置的读取保持 ADMIN 可见（日志中心页面仍可用）。</p>
 */
@RestController
@RequestMapping("/api/admin/backup")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class AdminBackupController {

    @Resource
    private SysBackupService sysBackupService;

    /**
     * 立即执行一次备份（同步，返回真实结果；仅超级管理员）
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @OperationLog(type = "admin_backup_trigger", description = "管理员手动触发数据库备份")
    public Result<Map<String, Object>> triggerBackup() {
        log.info("接收手动触发备份请求");

        SysBackupLog record = sysBackupService.triggerBackup(SysBackupServiceImpl.TYPE_MANUAL);

        boolean success = SysBackupServiceImpl.STATUS_SUCCESS.equals(record.getStatus());
        Map<String, Object> body = new HashMap<>();
        body.put("status", record.getStatus());
        body.put("fileName", record.getFileName());
        body.put("durationMs", record.getDurationMs());
        body.put("fileSize", record.getFileSize());
        body.put("message", success ? "备份成功" : record.getErrorMessage());

        // 备份失败也返回 200：前端按 body.status 区分成功/失败提示，
        // 走 HTTP 错误码会让它只弹出通用的「备份请求失败」，看不到具体原因
        return Result.success(success ? "备份成功" : "备份失败", body);
    }

    /**
     * 分页查询备份历史
     */
    @GetMapping("/history")
    public Result<Page<SysBackupLog>> getBackupHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("接收查询备份历史请求: page={}, size={}", page, size);
        return Result.success("备份历史获取成功", sysBackupService.getHistory(page, size));
    }

    /**
     * 查询最近一次备份
     */
    @GetMapping("/last")
    public Result<SysBackupLog> getLastBackup() {
        return Result.success("最近备份获取成功", sysBackupService.getLastBackup());
    }

    /**
     * 查询备份状态（最近一次记录 + 备份目录 + 是否有任务在执行）
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getBackupStatus() {
        return Result.success("备份状态获取成功", sysBackupService.getStatus());
    }

    /**
     * 读取备份设置
     */
    @GetMapping("/settings")
    public Result<Map<String, Object>> getBackupSettings() {
        return Result.success("备份设置获取成功", sysBackupService.getSettings());
    }

    /**
     * 更新备份设置（仅超级管理员）
     */
    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @OperationLog(type = "admin_backup_settings_update", description = "管理员更新备份设置")
    public Result<Void> updateBackupSettings(@RequestBody Map<String, Object> body) {
        Boolean enabled = body.get("enabled") == null ? null : Boolean.valueOf(body.get("enabled").toString());
        Integer retentionDays = null;
        Object retentionDaysRaw = body.get("retentionDays");
        if (retentionDaysRaw != null) {
            try {
                retentionDays = Integer.valueOf(retentionDaysRaw.toString());
            } catch (NumberFormatException e) {
                return Result.error(ResultCode.PARAM_TYPE_ERROR, "retentionDays 必须是整数");
            }
        }

        log.info("接收更新备份设置请求: enabled={}, retentionDays={}", enabled, retentionDays);
        sysBackupService.updateSettings(enabled, retentionDays);
        return Result.success("备份设置保存成功");
    }
}
