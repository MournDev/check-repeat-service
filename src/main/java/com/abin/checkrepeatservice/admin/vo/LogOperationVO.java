package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志VO
 */
@Data
public class LogOperationVO {
    
    @ExcelProperty("日志ID")
    private Long id;
    
    @ExcelProperty("操作时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    @ExcelProperty("用户名")
    private String username;
    
    @ExcelProperty("用户类型")
    private String userType;
    
    @ExcelProperty("操作类型")
    private String operation;
    
    @ExcelProperty("操作目标")
    private String target;
    
    @ExcelProperty("IP地址")
    private String ipAddress;
    
    @ExcelProperty("状态")
    private String status;
    
    @ExcelProperty("详细信息")
    private String details;
}