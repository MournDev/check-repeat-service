package com.abin.checkrepeatservice.teacher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 待审核论文列表查询DTO
 */
@Data
public class PendingReviewQueryDTO {
    
    /**
     * 教师ID（从token或用户信息中获取）
     */
    private String teacherId;
    
    /**
     * 优先级筛选：urgent/high/normal/""（全部）
     */
    private String priority;
    
    /**
     * 学院筛选：computer/electronic/mechanical/management/""（全部）
     */
    private String college;
    
    /**
     * 相似度范围：low/medium/high/""（全部）
     */
    private String similarityRange;
    
    /**
     * 排序字段：submitTime/deadline/waitingTime
     */
    private String sortField;
    
    /**
     * 排序方式：asc/desc
     */
    private String sortOrder;
    
    /**
     * 页码
     */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;
    
    /**
     * 每页大小
     */
    @Min(value = 1, message = "每页大小不能小于1")
    @Max(value = 100, message = "每页大小不能超过100")
    private Integer pageSize = 20;
}