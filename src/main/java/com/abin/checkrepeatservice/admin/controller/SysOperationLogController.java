package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.SysOperationLogService;
import com.abin.checkrepeatservice.admin.vo.OperationLogExcelVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.DateTimeUtils;
import com.abin.checkrepeatservice.pojo.entity.SysOperationLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
            LocalDateTime start = DateTimeUtils.parseStart(startTime);
            LocalDateTime end = DateTimeUtils.parseEndExclusive(endTime);

            Page<SysOperationLog> result = sysOperationLogService.getOperationLogPage(
                page, size, operationType, username, status, start, end);
            
            log.info("分页查询操作日志成功: 总记录数={}", result.getTotal());
            return Result.success("查询成功", result);
            
        } catch (Exception e) {
            log.error("分页查询操作日志失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询失败: " + "系统异常，请稍后重试");
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
            return Result.error(ResultCode.SYSTEM_ERROR, "获取失败: " + "系统异常，请稍后重试");
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
            return Result.error(ResultCode.SYSTEM_ERROR, "获取失败: " + "系统异常，请稍后重试");
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
            return Result.error(ResultCode.SYSTEM_ERROR, "获取失败: " + "系统异常，请稍后重试");
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
            return Result.error(ResultCode.SYSTEM_ERROR, "获取失败: " + "系统异常，请稍后重试");
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
            return Result.error(ResultCode.SYSTEM_ERROR, "获取失败: " + "系统异常，请稍后重试");
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
            return Result.error(ResultCode.SYSTEM_ERROR, "删除失败: " + "系统异常，请稍后重试");
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
            return Result.error(ResultCode.SYSTEM_ERROR, "清理失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 审计日志汇总（P5，仅超管）：按操作类型、用户类型两个维度分组计数。
     * 类级权限是 ADMIN，方法级叠加 SUPER_ADMIN 收敛到超管；时间范围可空=全量。
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<Map<String, Object>> getOperationLogSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("接收审计日志汇总请求: beginDate={}, endDate={}", beginDate, endDate);

        try {
            if (beginDate != null && endDate != null && beginDate.isAfter(endDate)) {
                return Result.error(ResultCode.PARAM_VALUE_INVALID, "beginDate 不能晚于 endDate");
            }
            // 结束日按"整日包含"换算为开区间上界（endDate 次日 0 点），与 XML 的 >= / < 口径一致
            LocalDateTime start = beginDate == null ? null : beginDate.atStartOfDay();
            LocalDateTime end = endDate == null ? null : endDate.plusDays(1).atStartOfDay();

            Map<String, Object> data = new HashMap<>();
            data.put("operationTypes", sysOperationLogService.summarizeByOperationType(start, end));
            data.put("userTypes", sysOperationLogService.summarizeByUserType(start, end));
            return Result.success("获取成功", data);

        } catch (Exception e) {
            log.error("获取审计日志汇总失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取失败: " + "系统异常，请稍后重试");
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
                                    @RequestParam(required = false) String userType,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String startDate,
                                    @RequestParam(required = false) String endDate,
                                    @RequestParam(required = false) String startTime,
                                    @RequestParam(required = false) String endTime) {

        // 日期参数有两套叫法，都必须收：前端 LogCenter 的「导出日志」传的是
        // startDate / endDate，而本方法原先只声明了 startTime / endTime。
        // Spring 对未声明的查询参数一律静默忽略，所以日期筛选一直没生效——
        // 用户选了时间范围导出，拿到的却是全量，且没有任何报错。
        // 新名字优先，保留下划线旧名兼容既有调用方。
        String startText = startDate != null ? startDate : startTime;
        String endText = endDate != null ? endDate : endTime;

        log.info("接收导出操作日志请求: operationType={}, userType={}, keyword={}, start={}, end={}",
                operationType, userType, keyword, startText, endText);

        try {
            LocalDateTime start = DateTimeUtils.parseStart(startText);
            LocalDateTime end = DateTimeUtils.parseEndExclusive(endText);

            List<SysOperationLog> logs = sysOperationLogService.exportOperationLogs(
                operationType, username, status, userType, start, end, keyword);

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

    /*
     * 原私有方法 parseDateTime 已删除：其兜底分支用 "yyyy-MM-dd" 去解析
     * "x 00:00:00"，多出的时间部分是 unparsed text，必然抛异常返回 null，
     * 导致纯日期输入的过滤条件被静默忽略。现已统一改用 DateTimeUtils。
     */
}