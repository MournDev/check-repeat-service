package com.abin.checkrepeatservice.student.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 查重历史响应DTO
 */
@Data
@ApiModel(description = "查重历史响应DTO")
public class CheckHistoryResponseDTO {
    
    @ApiModelProperty(value = "历史记录列表")
    private List<CheckHistoryDTO> history;
    
    @ApiModelProperty(value = "趋势分析")
    private TrendAnalysisDTO trendAnalysis;
    
    @ApiModelProperty(value = "论文信息")
    private PaperInfoDTO paperInfo;
    
    /**
     * 趋势分析DTO
     */
    @Data
    public static class TrendAnalysisDTO {
        @ApiModelProperty(value = "趋势方向")
        private String direction;
        
        @ApiModelProperty(value = "总改进值")
        private BigDecimal totalImprovement;
        
        @ApiModelProperty(value = "平均每次改进值")
        private BigDecimal averageImprovementPerVersion;
        
        @ApiModelProperty(value = "最佳版本")
        private Integer bestVersion;
    }
    
    /**
     * 论文信息DTO
     */
    @Data
    public static class PaperInfoDTO {
        @ApiModelProperty(value = "论文标题")
        private String title;
        
        @ApiModelProperty(value = "当前相似度")
        private BigDecimal currentSimilarity;
        
        @ApiModelProperty(value = "最低相似度")
        private BigDecimal lowestSimilarity;
        
        @ApiModelProperty(value = "版本数量")
        private Integer versionCount;
    }
}