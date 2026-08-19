package com.abin.checkrepeatservice.detection.dto;

import lombok.Data;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 相似度检测结果DTO
 */
@Data
public class SimilarityDetectionResult {
    
    /**
     * 目标论文ID
     */
    private Long targetPaperId;
    
    /**
     * 目标论文标题
     */
    private String targetPaperTitle;
    
    /**
     * 目标论文作者
     */
    private String targetAuthor;
    
    /**
     * 目标论文学院
     */
    private String targetCollege;
    
    /**
     * 总体相似度（百分比）
     */
    private Double overallSimilarity;
    
    /**
     * 风险等级
     */
    private String riskLevel;
    
    /**
     * 相似段落列表
     */
    private List<SimilaritySegment> similarSegments;
    
    /**
     * 总比对论文数
     */
    private Integer totalComparisons;
    
    /**
     * 检测时间
     */
    private Date detectionTime;
    
    /**
     * 建议措施
     */
    private List<String> recommendations;
    
    /**
     * 统计信息
     */
    private Map<String, Object> statistics;
}