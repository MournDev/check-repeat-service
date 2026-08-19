package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

/**
 * 添加学生请求DTO
 */
@Data
public class AddStudentDTO {
    /**
     * 学号（必填，唯一）
     */
    private String username;
    
    /**
     * 学生姓名（必填）
     */
    private String studentName;
    
    /**
     * 学院名称（必填）
     */
    private String collegeName;
    
    /**
     * 专业（必填）
     */
    private String major;
    
    /**
     * 年级（必填）
     */
    private String grade;
    
    /**
     * 邮箱（可选）
     */
    private String email;
    
    /**
     * 手机号（可选）
     */
    private String phone;
}