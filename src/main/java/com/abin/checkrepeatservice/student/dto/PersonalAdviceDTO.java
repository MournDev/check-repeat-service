package com.abin.checkrepeatservice.student.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 个性化学术建议DTO
 */
@Data
@ApiModel(description = "个性化学术建议DTO")
public class PersonalAdviceDTO {
    
    @ApiModelProperty(value = "报告版本号")
    private Integer version;
    
    @ApiModelProperty(value = "高风险区域列表")
    private List<HighRiskAreaDTO> highRiskAreas;
    
    @ApiModelProperty(value = "表现良好方面列表")
    private List<GoodAspectDTO> goodAspects;
    
    @ApiModelProperty(value = "通用改进建议列表")
    private List<String> generalTips;
    
    /**
     * 高风险区域DTO
     */
    @Data
    @ApiModel(description = "高风险区域DTO")
    public static class HighRiskAreaDTO {
        @ApiModelProperty(value = "章节名称")
        private String section;
        
        @ApiModelProperty(value = "相似度")
        private Double similarity;
        
        @ApiModelProperty(value = "问题描述")
        private String issue;
        
        @ApiModelProperty(value = "改进建议")
        private String suggestion;
    }
    
    /**
     * 表现良好方面DTO
     */
    @Data
    @ApiModel(description = "表现良好方面DTO")
    public static class GoodAspectDTO {
        @ApiModelProperty(value = "章节名称")
        private String section;
        
        @ApiModelProperty(value = "相似度")
        private Double similarity;
        
        @ApiModelProperty(value = "优势描述")
        private String strength;
        
        @ApiModelProperty(value = "鼓励话语")
        private String encouragement;
    }
}