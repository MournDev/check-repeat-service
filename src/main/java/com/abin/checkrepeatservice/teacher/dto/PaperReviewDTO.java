package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

import java.util.List;

/**
 * 论文审核请求DTO
 */
@Data
public class PaperReviewDTO {
    
    /**
     * 论文ID数组（支持批量）
     */
    private List<String> paperIds;
    
    /**
     * 审核状态：3-审核通过，4-审核不通过（退回修改）。
     * 旧的 1-通过 / 2-不通过 / 3-需要修改 约定已废弃，服务端不再接受。
     */
    private Integer reviewStatus;
    
    /**
     * 审核意见
     */
    private String reviewOpinion;
    
    /**
     * 审核附件
     */
    private String reviewAttach;
}