package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 分配记录统计信息DTO
 */
@Data
public class AssignmentHistoryStatsDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 总记录数
     */
    private Integer totalRecords = 0;
    
    /**
     * 有效分配数
     */
    private Integer activeAssignments = 0;
    
    /**
     * 涉及学生数
     */
    private Integer uniqueStudents = 0;
    
    /**
     * 涉及教师数
     */
    private Integer uniqueTeachers = 0;
}