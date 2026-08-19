package com.abin.checkrepeatservice.admin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全日志VO
 */
@Data
public class LogSecurityVO {
    
    @ExcelProperty("安全日志ID")
    private Long id;
    
    @ExcelProperty("时间戳")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    @ExcelProperty("事件类型")
    private String eventType;
    
    @ExcelProperty("用户名")
    private String username;
    
    @ExcelProperty("IP地址")
    private String ipAddress;
    
    @ExcelProperty("地理位置")
    private String location;
    
    @ExcelProperty("风险等级")
    private String riskLevel;
    
    @ExcelProperty("描述")
    private String description;
}