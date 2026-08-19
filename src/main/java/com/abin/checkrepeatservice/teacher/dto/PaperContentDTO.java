package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;
import java.util.List;

/**
 * 论文内容响应DTO
 */
@Data
public class PaperContentDTO {
    
    /**
     * 论文ID
     */
    private String paperId;
    
    /**
     * 论文标题
     */
    private String title;
    
    /**
     * 论文正文内容
     */
    private String content;
    
    /**
     * 摘要
     */
    private String abstractText;
    
    /**
     * 关键词
     */
    private List<String> keywords;
    
    /**
     * 字数统计
     */
    private Integer wordCount;
    
    /**
     * 页数
     */
    private Integer pageCount;
    
    /**
     * 文件大小
     */
    private String fileSize;
    
    /**
     * 文件类型
     */
    private String fileType;
}