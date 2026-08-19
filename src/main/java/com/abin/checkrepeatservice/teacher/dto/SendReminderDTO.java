package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

import java.util.List;

/**
 * 发送提醒消息DTO
 */
@Data
public class SendReminderDTO {
    
    /**
     * 学生ID数组
     */
    private List<String> studentIds;
    
    /**
     * 提醒内容
     */
    private String message;
    
    /**
     * 相关论文ID（可选）
     */
    private List<String> paperIds;
}