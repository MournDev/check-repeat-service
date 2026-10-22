package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 自动分配进度DTO
 */
@Data
public class AutoAssignmentProgressDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 任务状态
     * running: 运行中
     * completed: 已完成
     * failed: 失败
     * cancelled: 已取消
     */
    private String status;
    
    /**
     * 当前步骤描述
     */
    private String currentStep;
    
    /**
     * 已处理数量
     */
    private Integer processedCount;
    
    /**
     * 总数量
     */
    private Integer totalCount;
    
    /**
     * 详细分配信息
     */
    private List<AssignmentDetail> details;
    
    /**
     * 分配详情内部类
     */
    @Data
    public static class AssignmentDetail {
        /** 论文ID（结果应用/回放时定位论文） */
        private String paperId;
        private String studentName;
        private String studentId;
        private String teacherName;
        private String teacherId;
        private Boolean conflict;
        private Integer matchScore;
        private String reason;
    }
}