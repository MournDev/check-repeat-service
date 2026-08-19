package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 可用教师信息DTO
 */
@Data
public class AvailableTeacherDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 教师ID
     */
    private String id;
    
    /**
     * 教师姓名
     */
    private String name;
    
    /**
     * 职称
     */
    private String title;
    
    /**
     * 所属部门
     */
    private String department;
    
    /**
     * 当前指导学生数
     */
    private Integer currentLoad;
    
    /**
     * 最大指导学生数
     */
    private Integer maxLoad;
}