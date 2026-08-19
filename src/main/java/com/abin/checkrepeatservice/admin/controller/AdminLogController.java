package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.mapper.SysOperationLogMapper;
import com.abin.checkrepeatservice.admin.vo.LoginLogExcelVO;
import com.abin.checkrepeatservice.admin.vo.OperationLogExcelVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.SysLoginLog;
import com.abin.checkrepeatservice.pojo.entity.SysOperationLog;
import com.abin.checkrepeatservice.user.mapper.SysLoginLogMapper;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
public class AdminLogController {

    @Resource
    private SysLoginLogMapper sysLoginLogMapper;
    
    @Resource
    private SysOperationLogMapper sysOperationLogMapper;

    /**
     * 获取操作日志列表
     */
    @GetMapping("/operations")
    public Result<Page<SysOperationLog>> getOperationLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword) {
        
        Page<SysOperationLog> logPage = new Page<>(page, size);
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        
        // 操作类型筛选
        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq(SysOperationLog::getOperationType, operationType);
        }
        
        // 时间范围筛选
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(SysOperationLog::getOperationTime, LocalDateTime.parse(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(SysOperationLog::getOperationTime, LocalDateTime.parse(endDate));
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

    /**
     * 导出操作日志
     */
    @GetMapping("/operations/export")
    public void exportOperationLogs(HttpServletResponse response,
                                  @RequestParam(required = false) String operationType,
                                  @RequestParam(required = false) String startDate,
                                  @RequestParam(required = false) String endDate,
                                  @RequestParam(required = false) String keyword) throws IOException {
        
        try {
            LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
            
            // 应用筛选条件
            if (operationType != null && !operationType.isEmpty()) {
                wrapper.eq(SysOperationLog::getOperationType, operationType);
            }
            if (startDate != null && !startDate.isEmpty()) {
                wrapper.ge(SysOperationLog::getOperationTime, LocalDateTime.parse(startDate));
            }
            if (endDate != null && !endDate.isEmpty()) {
                wrapper.le(SysOperationLog::getOperationTime, LocalDateTime.parse(endDate));
            }
            if (keyword != null && !keyword.isEmpty()) {
                wrapper.and(w -> w.like(SysOperationLog::getUserName, keyword)
                        .or()
                        .like(SysOperationLog::getDescription, keyword));
            }
            
            wrapper.orderByDesc(SysOperationLog::getOperationTime);
            List<SysOperationLog> logs = sysOperationLogMapper.selectList(wrapper);
            
            // 转换为Excel VO
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
            
            // 设置响应头
            setExcelResponseHeader(response, "操作日志");
            
            // 使用EasyExcel写入数据
            EasyExcel.write(response.getOutputStream(), OperationLogExcelVO.class)
                    .sheet("操作日志")
                    .doWrite(excelData);
                    
        } catch (Exception e) {
            log.error("导出操作日志失败", e);
            response.reset();
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":500,\"message\":\"导出失败：" + e.getMessage() + "\"}");
        }
    }

    /**
     * 导出登录日志
     */
    @GetMapping("/login/export")
    public void exportLoginLogs(HttpServletResponse response,
                              @RequestParam(required = false) String ip,
                              @RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String endDate) throws IOException {
        
        try {
            LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
            
            // 应用筛选条件
            if (ip != null && !ip.isEmpty()) {
                wrapper.like(SysLoginLog::getLoginIp, ip);
            }
            if (startDate != null && !startDate.isEmpty()) {
                wrapper.ge(SysLoginLog::getLoginTime, LocalDateTime.parse(startDate));
            }
            if (endDate != null && !endDate.isEmpty()) {
                wrapper.le(SysLoginLog::getLoginTime, LocalDateTime.parse(endDate));
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
    @GetMapping("/login")
    public Result<Page<SysLoginLog>> getLoginLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        Page<SysLoginLog> logPage = new Page<>(page, size);
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        
        // IP地址筛选
        if (ip != null && !ip.isEmpty()) {
            wrapper.like(SysLoginLog::getLoginIp, ip);
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
        return Result.success("登录日志列表获取成功", resultPage);
    }

    /**
     * 获取系统日志统计
     */
    @GetMapping("/system")
    public Result<Map<String, Object>> getSystemLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        Map<String, Object> stats = new HashMap<>();
        
        // 获取操作日志统计
        LambdaQueryWrapper<SysOperationLog> operationWrapper = new LambdaQueryWrapper<>();
        if (level != null && !level.isEmpty()) {
            operationWrapper.eq(SysOperationLog::getOperationType, level);
        }
        if (startDate != null && !startDate.isEmpty()) {
            operationWrapper.ge(SysOperationLog::getOperationTime, LocalDateTime.parse(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            operationWrapper.le(SysOperationLog::getOperationTime, LocalDateTime.parse(endDate));
        }
        
        Long operationCount = sysOperationLogMapper.selectCount(operationWrapper);
        stats.put("operationLogCount", operationCount);
        
        // 获取登录日志统计
        LambdaQueryWrapper<SysLoginLog> loginWrapper = new LambdaQueryWrapper<>();
        if (startDate != null && !startDate.isEmpty()) {
            loginWrapper.ge(SysLoginLog::getLoginTime, LocalDateTime.parse(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            loginWrapper.le(SysLoginLog::getLoginTime, LocalDateTime.parse(endDate));
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