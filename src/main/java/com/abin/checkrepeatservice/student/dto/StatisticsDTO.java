package com.abin.checkrepeatservice.student.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 统计分析数据DTO
 */
@Data
@ApiModel(description = "统计分析数据DTO")
public class StatisticsDTO {
    
    @ApiModelProperty(value = "改进率")
    private Integer improvementRate;
    
    @ApiModelProperty(value = "平均相似度")
    private BigDecimal averageSimilarity;
    
    @ApiModelProperty(value = "改进速度")
    private String improvementSpeed;
    
    @ApiModelProperty(value = "总查重次数")
    private Integer totalChecks;
    
    @ApiModelProperty(value = "首次查重相似度")
    private BigDecimal firstCheckSimilarity;
    
    @ApiModelProperty(value = "最新查重相似度")
    private BigDecimal latestCheckSimilarity;
}