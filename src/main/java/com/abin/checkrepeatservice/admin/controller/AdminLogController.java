package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.mapper.SysOperationLogMapper;
import com.abin.checkrepeatservice.admin.vo.LoginLogExcelVO;
import com.abin.checkrepeatservice.admin.vo.OperationLogExcelVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.utils.DateTimeUtils;
import com.abin.checkrepeatservice.pojo.entity.SysLoginLog;
import com.abin.checkrepeatservice.pojo.entity.SysOperationLog;
import com.abin.checkrepeatservice.user.mapper.SysLoginLogMapper;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员日志中心控制器
 */
@RestController
@RequestMapping("/api/admin/logs")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
@Tag(name = "日志中心", description = "操作日志/登录日志/系统日志统计")
public class AdminLogController {

    @Resource
    private SysLoginLogMapper sysLoginLogMapper;
    
    @Resource
    private SysOperationLogMapper sysOperationLogMapper;

    /**
     * 获取操作日志列表
     */
    @Operation(summary = "获取操作日志列表", description = "分页查询操作日志，支持类型、用户类型、时间范围与关键字筛选")
    @GetMapping("/operations")
    public Result<Page<SysOperationLog>> getOperationLogs(
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "操作类型（可选）")
            @RequestParam(required = false) String operationType,
            @Parameter(description = "用户类型（可选）")
            @RequestParam(required = false) String userType,
            @Parameter(description = "开始日期（可选）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（可选）")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "关键字，匹配用户名或描述（可选）")
            @RequestParam(required = false) String keyword) {

        Page<SysOperationLog> logPage = new Page<>(page, size);
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();

        // 操作类型筛选
        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq(SysOperationLog::getOperationType, operationType);
        }

        // 用户类型筛选（前端一直在传，此前后端未声明该参数，被 Spring 静默忽略）
        if (userType != null && !userType.isEmpty()) {
            wrapper.eq(SysOperationLog::getUserType, userType);
        }

        // 时间范围筛选（endDate 为开区间上界，见 DateTimeUtils.parseEndExclusive）
        LocalDateTime start = DateTimeUtils.parseStart(startDate);
        LocalDateTime endExclusive = DateTimeUtils.parseEndExclusive(endDate);
        if (start != null) {
            wrapper.ge(SysOperationLog::getOperationTime, start);
        }
        if (endExclusive != null) {
            wrapper.lt(SysOperationLog::getOperationTime, endExclusive);
        }

        // 关键字搜索
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysOperationLog::getUserName, keyword)
                    .or()
                    .like(SysOperationLog::getDescription, keyword));
        }

        // 按操作时间倒序
        wrapper.orderByDesc(SysOperationLog::getOperationTime);

        Page<SysOperationLog> resultPage = sysOperationLogMapper.selectPage(logPage, wrapper);
        return Result.success("操作日志列表获取成功", resultPage);
    }

    /*
     * 原 GET /operations/export（导出操作日志）已删除。
     *
     * 它是死接口：前端从未调用过。前端 LogCenter 的「导出日志」用的是
     * /api/admin/operation-logs/export（logs.js 里的 exportOperationLogsNew），
     * 本控制器对应的 wrapper `exportOperationLogs`（logs.js:39）没有任何 import，
     * 该 URL 在整个前端仓库只出现在它自己的定义里。
     *
     * 两个导出端点功能重复，且导出语义（日期用 startDate/endDate 半开区间、
     * keyword 匹配用户名或描述）已在 /api/admin/operation-logs/export 上对齐，
     * 故移除本处，避免留下一个无人调用、却与在用的那个行为不同的副本。
     */

    /**
     * 导出登录日志
     */
    @Operation(summary = "导出登录日志", description = "按 IP 与时间范围导出登录日志为 Excel")
    @GetMapping("/login/export")
    public void exportLoginLogs(HttpServletResponse response,
                              @Parameter(description = "登录IP（可选，模糊匹配）")
                              @RequestParam(required = false) String ip,
                              @Parameter(description = "开始日期（可选）")
                              @RequestParam(required = false) String startDate,
                              @Parameter(description = "结束日期（可选）")
                              @RequestParam(required = false) String endDate) throws IOException {
        
        try {
            LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
            
            // 应用筛选条件
            if (ip != null && !ip.isEmpty()) {
                wrapper.like(SysLoginLog::getLoginIp, ip);
            }
            LocalDateTime start = DateTimeUtils.parseStart(startDate);
            LocalDateTime endExclusive = DateTimeUtils.parseEndExclusive(endDate);
            if (start != null) {
                wrapper.ge(SysLoginLog::getLoginTime, start);
            }
            if (endExclusive != null) {
                wrapper.lt(SysLoginLog::getLoginTime, endExclusive);
            }
            
            wrapper.orderByDesc(SysLoginLog::getLoginTime);
            List<SysLoginLog> logs = sysLoginLogMapper.selectList(wrapper);
            
            // 转换为Excel VO
            List<LoginLogExcelVO> excelData = logs.stream().map(log -> {
                LoginLogExcelVO vo = new LoginLogExcelVO();
                vo.setId(log.getId());
                vo.setUsername(log.getUsername());
                vo.setLoginIp(log.getLoginIp());
                vo.setLoginLocation(log.getLoginLocation());
                vo.setLoginDevice(log.getLoginDevice());
                // 登录结果转换
                String loginResult = log.getLoginResult() == 1 ? "成功" : "失败";
                vo.setLoginResult(loginResult);
                vo.setFailReason(log.getFailReason());
                vo.setLoginTime(log.getLoginTime());
                return vo;
            }).collect(Collectors.toList());
            
            // 设置响应头
            setExcelResponseHeader(response, "登录日志");
            
            // 使用EasyExcel写入数据
            EasyExcel.write(response.getOutputStream(), LoginLogExcelVO.class)
                    .sheet("登录日志")
                    .doWrite(excelData);
                    
        } catch (Exception e) {
            log.error("导出登录日志失败", e);
            response.reset();
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":500,\"message\":\"导出失败：" + e.getMessage() + "\"}");
        }
    }

    /**
     * 设置Excel导出响应头
     */
    private void setExcelResponseHeader(HttpServletResponse response, String fileName) throws IOException {
        // 获取当前请求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        
        // 处理中文文件名编码
        String userAgent = request != null ? request.getHeader("User-Agent") : "";
        String encodedFileName;
        
        if (userAgent != null && userAgent.contains("MSIE")) {
            // IE浏览器
            encodedFileName = URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8.name());
        } else if (userAgent != null && userAgent.contains("Mozilla")) {
            // Firefox/Chrome浏览器
            encodedFileName = new String((fileName + ".xlsx").getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1.name());
        } else {
            // 其他浏览器
            encodedFileName = URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8.name());
        }
        
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    /**
     * 获取登录日志列表
     */
    @Operation(summary = "获取登录日志列表", description = "分页查询登录日志，支持 IP 与时间范围筛选")
    @GetMapping("/login")
    public Result<Page<SysLoginLog>> getLoginLogs(
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "登录IP（可选，模糊匹配）")
            @RequestParam(required = false) String ip,
            @Parameter(description = "开始日期（可选）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（可选）")
            @RequestParam(required = false) String endDate) {
        
        Page<SysLoginLog> logPage = new Page<>(page, size);
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        
        // IP地址筛选
        if (ip != null && !ip.isEmpty()) {
            wrapper.like(SysLoginLog::getLoginIp, ip);
        }

        // 时间范围筛选（endDate 为开区间上界）
        LocalDateTime start = DateTimeUtils.parseStart(startDate);
        LocalDateTime endExclusive = DateTimeUtils.parseEndExclusive(endDate);
        if (start != null) {
            wrapper.ge(SysLoginLog::getLoginTime, start);
        }
        if (endExclusive != null) {
            wrapper.lt(SysLoginLog::getLoginTime, endExclusive);
        }
        
        // 按登录时间倒序
        wrapper.orderByDesc(SysLoginLog::getLoginTime);
        
        Page<SysLoginLog> resultPage = sysLoginLogMapper.selectPage(logPage, wrapper);
        return Result.success("登录日志列表获取成功", resultPage);
    }

    /**
     * 获取系统日志统计
     */
    @Operation(summary = "获取系统日志统计", description = "统计操作日志与登录日志数量及操作类型分布")
    @GetMapping("/system")
    public Result<Map<String, Object>> getSystemLogs(
            @Parameter(description = "日志级别/操作类型（可选）")
            @RequestParam(required = false) String level,
            @Parameter(description = "开始日期（可选）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（可选）")
            @RequestParam(required = false) String endDate) {
        
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime start = DateTimeUtils.parseStart(startDate);
        LocalDateTime endExclusive = DateTimeUtils.parseEndExclusive(endDate);

        // 获取操作日志统计
        LambdaQueryWrapper<SysOperationLog> operationWrapper = new LambdaQueryWrapper<>();
        if (level != null && !level.isEmpty()) {
            operationWrapper.eq(SysOperationLog::getOperationType, level);
        }
        if (start != null) {
            operationWrapper.ge(SysOperationLog::getOperationTime, start);
        }
        if (endExclusive != null) {
            operationWrapper.lt(SysOperationLog::getOperationTime, endExclusive);
        }

        Long operationCount = sysOperationLogMapper.selectCount(operationWrapper);
        stats.put("operationLogCount", operationCount);

        // 获取登录日志统计
        LambdaQueryWrapper<SysLoginLog> loginWrapper = new LambdaQueryWrapper<>();
        if (start != null) {
            loginWrapper.ge(SysLoginLog::getLoginTime, start);
        }
        if (endExclusive != null) {
            loginWrapper.lt(SysLoginLog::getLoginTime, endExclusive);
        }
        
        Long loginCount = sysLoginLogMapper.selectCount(loginWrapper);
        stats.put("loginLogCount", loginCount);
        
        // 按操作类型统计操作日志
        Map<String, Long> operationTypeStats = new HashMap<>();
        operationTypeStats.put("登录", sysOperationLogMapper.selectCount(
                new LambdaQueryWrapper<SysOperationLog>().eq(SysOperationLog::getOperationType, "login")));
        operationTypeStats.put("论文上传", sysOperationLogMapper.selectCount(
                new LambdaQueryWrapper<SysOperationLog>().eq(SysOperationLog::getOperationType, "paper_upload")));
        operationTypeStats.put("查重提交", sysOperationLogMapper.selectCount(
                new LambdaQueryWrapper<SysOperationLog>().eq(SysOperationLog::getOperationType, "task_submit")));
        stats.put("operationTypeStats", operationTypeStats);
        
        return Result.success("系统日志统计获取成功", stats);
    }
}