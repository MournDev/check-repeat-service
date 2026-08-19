package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

/**
 * 论文预览URL响应DTO
 */
@Data
public class PaperPreviewUrlDTO {
    
    /**
     * 论文ID
     */
    private String paperId;
    
    /**
     * 预览URL
     */
    private String previewUrl;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * KKFileView服务器地址
     */
    private String kkFileViewServer;
}