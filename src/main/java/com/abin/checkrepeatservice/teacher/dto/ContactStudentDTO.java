package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

/**
 * 联系学生DTO
 */
@Data
public class ContactStudentDTO {
    
    /**
     * 学生ID
     */
    private String studentId;
    
    /**
     * 论文ID
     */
    private String paperId;
    
    /**
     * 联系内容
     */
    private String message;
    
    /**
     * 消息类型：chat/email/system
     */
    private String messageType;
}