package com.abin.checkrepeatservice.student.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 版本对比响应DTO
 */
@Data
@ApiModel(description = "版本对比响应DTO")
public class VersionCompareResponseDTO {
    
    @ApiModelProperty(value = "起始版本")
    private Integer fromVersion;
    
    @ApiModelProperty(value = "目标版本")
    private Integer toVersion;
    
    @ApiModelProperty(value = "总体变化")
    private BigDecimal overallChange;
    
    @ApiModelProperty(value = "章节对比列表")
    private List<SectionComparisonDTO> sectionComparison;
    
    /**
     * 章节对比DTO
     */
    @Data
    public static class SectionComparisonDTO {
        @ApiModelProperty(value = "章节名称")
        private String name;
        
        @ApiModelProperty(value = "起始版本相似度")
        private BigDecimal from;
        
        @ApiModelProperty(value = "目标版本相似度")
        private BigDecimal to;
        
        @ApiModelProperty(value = "变化值")
        private BigDecimal change;
    }
}