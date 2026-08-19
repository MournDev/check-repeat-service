package com.abin.checkrepeatservice.student.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 相似度趋势数据DTO
 */
@Data
@ApiModel(description = "相似度趋势数据DTO")
public class SimilarityTrendDTO {
    
    @ApiModelProperty(value = "日期列表")
    private List<String> dates;
    
    @ApiModelProperty(value = "版本标签列表")
    private List<String> versions;
    
    @ApiModelProperty(value = "相似度列表")
    private List<BigDecimal> similarities;
    
    // 兼容旧版本的setter方法
    public void setVersions(List<String> versions) {
        this.versions = versions;
    }
}