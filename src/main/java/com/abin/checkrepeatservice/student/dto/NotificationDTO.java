package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

import java.util.Date;

/**
 * 通知消息DTO
 */
@Data
public class NotificationDTO {
    private Long id;
    private String type;        // success/warning/info/error
    private String title;
    private String content;
    private Date time;
    private Boolean isRead;
    private String relatedPaperId; // 关联的论文ID
}