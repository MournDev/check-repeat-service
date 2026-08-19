package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自动分配历史记录DTO
 */
@Data
public class AutoAssignmentHistoryDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 历史记录ID
     */
    private String id;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 使用的分配策略
     */
    private String strategy;
    
    /**
     * 总学生数
     */
    private Integer totalStudents;
    
    /**
     * 成功分配数
     */
    private Integer assignedCount;
    
    /**
     * 成功率百分比
     */
    private Double successRate;
    
    /**
     * 执行时长(毫秒)
     */
    private Long duration;
    
    /**
     * 任务状态
     * completed: 已完成
     * failed: 失败
     * cancelled: 已取消
     */
    private String status;
    
    /**
     * 操作人ID
     */
    private Long operatorId;
    
    /**
     * 操作人姓名
     */
    private String operatorName;
    
    /**
     * 备注
     */
    private String remark;
}