package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

/**
 * 时间节点DTO
 */
@Data
public class DeadlinesDTO {
    private String submissionDeadline;  // 论文提交截止日期
    private String reviewDeadline;      // 审核截止日期
    private String defenseDate;         // 答辩时间
    private String graduationDate;      // 预计毕业时间
}