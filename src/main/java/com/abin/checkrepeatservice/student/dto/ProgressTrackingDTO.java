package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 进度跟踪DTO
 */
@Data
public class ProgressTrackingDTO {
    private Integer currentStep;        // 当前步骤：0-未开始 1-已提交 2-审核中 3-需修改 4-已完成
    private String estimatedCompletion; // 预计完成时间
    private String processingSpeed;     // 处理速度评价
    private List<ProgressStepDTO> steps; // 步骤详情
    
    @Data
    public static class ProgressStepDTO {
        private Integer step;
        private String name;
        private String status;      // finish/process/wait/error
        private Date completedTime;
        private String description;
    }
}