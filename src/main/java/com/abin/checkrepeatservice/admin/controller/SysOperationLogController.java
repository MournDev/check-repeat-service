package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.SysOperationLogService;
import com.abin.checkrepeatservice.admin.vo.OperationLogExcelVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SysOperationLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 操作日志管理控制器
 */
@RestController
@RequestMapping("/api/admin/operation-logs")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class SysOperationLogController {

    @Resource
    private SysOperationLogService sysOperationLogService;

    /**
     * 分页查询操作日志
     */
    @GetMapping("/page")
    public Result<Page<SysOperationLog>> getOperationLogPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        
        log.info("接收分页查询操作日志请求: page={}, size={}, type={}, user={}, status={}", 
                page, size, operationType, username, status);
        
        try {
            LocalDateTime start = parseDateTime(startTime);
            LocalDateTime end = parseDateTime(endTime);
            
            Page<SysOperationLog> result = sysOperationLogService.getOperationLogPage(
                page, size, operationType, username, status, start, end);
            
            log.info("分页查询操作日志成功: 总记录数={}", result.getTotal());
            return Result.success("查询成功", result);
            
        } catch (Exception e) {
            log.error("分页查询操作日志失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取操作日志详情
     */
    @GetMapping("/{id}")
    public Result<SysOperationLog> getOperationLogDetail(@PathVariable Long id) {
        log.info("接收获取操作日志详情请求: id={}", id);
        
        try {
            SysOperationLog logDetail = sysOperationLogService.getOperationLogById(id);
            if (logDetail == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "日志不存在");
            }
            
            return Result.success("获取成功", logDetail);
            
        } catch (Exception e) {
            log.error("获取操作日志详情失败: id={}, error={}", id, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取操作统计信息
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getOperationStatistics(
            @RequestParam(defaultValue = "7") Integer days) {
        
        log.info("接收获取操作统计信息请求: days={}", days);
        
        try {
            Map<String, Object> statistics = sysOperationLogService.getOperationStatistics(days);
            return Result.success("获取成功", statistics);
            
        } catch (Exception e) {
            log.error("获取操作统计信息失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取热门操作统计
     */
    @GetMapping("/hot-operations")
    public Result<List<Map<String, Object>>> getHotOperations(
            @RequestParam(defaultValue = "7") Integer days,
            @RequestParam(defaultValue = "10") Integer limit) {
        
        log.info("接收获取热门操作统计请求: days={}, limit={}", days, limit);
        
        try {
            List<Map<String, Object>> hotOperations = sysOperationLogService.getHotOperations(days, limit);
            return Result.success("获取成功", hotOperations);
            
        } catch (Exception e) {
            log.error("获取热门操作统计失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户活跃度统计
     */
    @GetMapping("/user-activity")
    public Result<List<Map<String, Object>>> getUserActivityStatistics(
            @RequestParam(defaultValue = "7") Integer days) {
        
        log.info("接收获取用户活跃度统计请求: days={}", days);
        
        try {
            List<Map<String, Object>> userActivity = sysOperationLogService.getUserActivityStatistics(days);
            return Result.success("获取成功", userActivity);
            
        } catch (Exception e) {
            log.error("获取用户活跃度统计失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取模块使用统计
     */
    @GetMapping("/module-usage")
    public Result<List<Map<String, Object>>> getModuleUsageStatistics(
            @RequestParam(defaultValue = "7") Integer days) {
        
        log.info("接收获取模块使用统计请求: days={}", days);
        
        try {
            List<Map<String, Object>> moduleUsage = sysOperationLogService.getModuleUsageStatistics(days);
            return Result.success("获取成功", moduleUsage);
            
        } catch (Exception e) {
            log.error("获取模块使用统计失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除操作日志
     */
    @DeleteMapping("/batch")
    public Result<String> batchDeleteOperationLogs(@RequestBody List<Long> ids) {
        log.info("接收批量删除操作日志请求: ids={}", ids);
        
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "请选择要删除的日志");
            }
            
            boolean result = sysOperationLogService.batchDeleteOperationLogs(ids);
            if (result) {
                log.info("批量删除操作日志成功: 删除条数={}", ids.size());
                return Result.success("删除成功");
            } else {
                return Result.error(ResultCode.SYSTEM_ERROR, "删除失败");
            }
            
        } catch (Exception e) {
            log.error("批量删除操作日志失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期操作日志
     */
    @DeleteMapping("/clean-expired")
    public Result<String> cleanExpiredLogs(@RequestParam(defaultValue = "30") Integer days) {
        log.info("接收清理过期操作日志请求: days={}", days);
        
        try {
            boolean result = sysOperationLogService.cleanExpiredLogs(days);
            if (result) {
                log.info("清理过期操作日志成功");
                return Result.success("清理成功");
            } else {
                return Result.error(ResultCode.SYSTEM_ERROR, "清理失败");
            }
            
        } catch (Exception e) {
            log.error("清理过期操作日志失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "清理失败: " + e.getMessage());
        }
    }

    /**
     * 导出操作日志
     */
    @GetMapping("/export")
    public void exportOperationLogs(HttpServletResponse response,
                                    @RequestParam(required = false) String operationType,
                                    @RequestParam(required = false) String username,
                                    @RequestParam(required = false) Integer status,
                                    @RequestParam(required = false) String startTime,
                                    @RequestParam(required = false) String endTime) {
        
        log.info("接收导出操作日志请求");
        
        try {
            LocalDateTime start = parseDateTime(startTime);
            LocalDateTime end = parseDateTime(endTime);
            
            List<SysOperationLog> logs = sysOperationLogService.exportOperationLogs(
                operationType, username, status, start, end);
            
            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = "操作日志_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
            
            // 实现Excel导出逻辑
            //为Excel VO
            List<OperationLogExcelVO> excelData = logs.stream().map(log -> {
                OperationLogExcelVO vo = new OperationLogExcelVO();
                vo.setId(log.getId());
                vo.setUsername(log.getUserName());
                vo.setOperationType(log.getOperationType());
                vo.setDescription(log.getDescription());
                vo.setIpAddress(log.getIpAddress());
                vo.setOperationTime(log.getOperationTime());
                return vo;
            }).collect(Collectors.toList());
            
            // 使用EasyExcel导出
            com.alibaba.excel.EasyExcel.write(response.getOutputStream(), OperationLogExcelVO.class)
                    .sheet("操作日志")
                    .doWrite(excelData);
            
            log.info("导出操作日志成功: 记录数={}", logs.size());
            
        } catch (Exception e) {
            log.error("导出操作日志失败: {}", e.getMessage(), e);
            try {
                response.getWriter().write("{\"code\":500,\"message\":\"导出失败：" + e.getMessage() + "\"}");
            } catch (Exception ex) {
                log.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 解析日期时间字符串
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(dateTimeStr, formatter);
        } catch (Exception e) {
            log.warn("日期时间格式解析失败: {}, 使用默认格式尝试", dateTimeStr);
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return LocalDateTime.parse(dateTimeStr + " 00:00:00", formatter);
            } catch (Exception ex) {
                log.error("日期时间解析完全失败: {}", dateTimeStr);
                return null;
            }
        }
    }
}