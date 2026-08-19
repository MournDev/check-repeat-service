package com.abin.checkrepeatservice.detection.dto;

import lombok.Data;
import java.util.List;

/**
 * 相似段落DTO
 * 用于表示与目标论文相比的单个相似段落信息
 */
@Data
public class SimilaritySegment {
    
    /**
     * 相似论文ID
     */
    private Long paperId;
    
    /**
     * 相似论文标题
     */
    private String paperTitle;
    
    /**
     * 相似论文作者
     */
    private String author;
    
    /**
     * 相似论文学院
     */
    private String college;
    
    /**
     * 相似度（百分比）
     */
    private Double similarity;
    
    /**
     * 重复片段列表
     */
    private List<String> repeatedFragments;
    
    /**
     * 相似段落内容
     */
    private String segmentContent;
    
    /**
     * 在源论文中的位置
     */
    private Integer positionInSource;
    
    /**
     * 在目标论文中的位置
     */
    private Integer positionInTarget;
}