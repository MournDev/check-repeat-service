package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

/**
 * 委托审核DTO
 */
@Data
public class DelegateReviewDTO {
    
    /**
     * 论文ID
     */
    private String paperId;
    
    /**
     * 委托教师ID
     */
    private String delegateTeacherId;
    
    /**
     * 委托原因
     */
    private String reason;
}