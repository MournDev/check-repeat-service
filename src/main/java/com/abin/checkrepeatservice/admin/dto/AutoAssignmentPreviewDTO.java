package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 自动分配预览数据DTO
 */
@Data
public class AutoAssignmentPreviewDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 待分配学生数
     */
    private Integer unassigned = 0;
    
    /**
     * 可用教师数
     */
    private Integer availableTeachers = 0;
    
    /**
     * 预期分配数
     */
    private Integer expectedAssigned = 0;
    
    /**
     * 潜在冲突数
     */
    private Integer potentialConflicts = 0;
    
    /**
     * 按专业统计
     */
    private List<MajorStat> majorStats;
    
    /**
     * 按教师负载统计
     */
    private List<TeacherLoadStat> teacherLoadStats;
    
    /**
     * 专业统计内部类
     */
    @Data
    public static class MajorStat {
        private String majorId;
        private String majorName;
        private Integer studentCount;
        private Integer teacherCount;
        private Double matchRate;
    }
    
    /**
     * 教师负载统计内部类
     */
    @Data
    public static class TeacherLoadStat {
        private String teacherId;
        private String teacherName;
        private Integer currentLoad;
        private Integer maxLoad;
        private Integer availableSlots;
    }
}