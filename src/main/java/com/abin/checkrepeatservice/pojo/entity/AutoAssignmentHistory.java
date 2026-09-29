package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自动分配历史记录实体类
 */
@Data
@TableName("auto_assignment_history")
public class AutoAssignmentHistory implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    /**
     * 任务ID
     */
    private String taskId;
    
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
     * 任务状态 (completed/failed/cancelled)
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
     * 配置详情(JSON格式)
     */
    private String configDetail;
    
    /**
     * 分配结果详情(JSON格式)
     */
    private String resultDetail;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 创建时间（由库的 DEFAULT CURRENT_TIMESTAMP 填充，调用方【不要】再 setCreateTime）
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    /**
     * 是否删除 0-未删除 1-已删除
     */
    @TableLogic
    private Integer isDeleted;
}