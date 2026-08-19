package com.abin.checkrepeatservice.teacher.vo;

import lombok.Data;

/**
 * 查重报告VO
 */
@Data
public class PlagiarismReportVO {
    
    /**
     * 报告ID
     */
    private String reportId;
    
    /**
     * 论文ID
     */
    private String paperId;
    
    /**
     * 相似度
     */
    private Integer similarity;
    
    /**
     * 详细结果
     */
    private Object detailedResults;
    
    /**
     * 生成时间
     */
    private String generatedTime;
    
    /**
     * 报告URL
     */
    private String reportUrl;
}