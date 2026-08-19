package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分配记录列表项DTO
 */
@Data
public class AssignmentRecordDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 记录ID
     */
    private String id;
    
    /**
     * 学生姓名
     */
    private String studentName;
    
    /**
     * 学号
     */
    private String studentId;
    
    /**
     * 专业代码
     */
    private String major;
    
    /**
     * 年级
     */
    private String grade;
    
    /**
     * 指导老师姓名
     */
    private String teacherName;
    
    /**
     * 教师职称
     */
    private String teacherTitle;
    
    /**
     * 所在院系
     */
    private String department;
    
    /**
     * 分配类型
     */
    private String assignmentType;
    
    /**
     * 分配时间
     */
    private LocalDateTime assignTime;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 操作人
     */
    private String operator;
    
    /**
     * 操作时间
     */
    private LocalDateTime operateTime;
    
    /**
     * 分配原因
     */
    private String reason;
    
    /**
     * 备注说明
     */
    private String notes;
}