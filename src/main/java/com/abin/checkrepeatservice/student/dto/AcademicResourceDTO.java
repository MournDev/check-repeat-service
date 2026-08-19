package com.abin.checkrepeatservice.student.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 学术资源DTO
 */
@Data
@ApiModel(description = "学术资源DTO")
public class AcademicResourceDTO {
    
    @ApiModelProperty(value = "资源ID")
    private Long resourceId;
    
    @ApiModelProperty(value = "资源标题")
    private String title;
    
    @ApiModelProperty(value = "资源类型")
    private String type;
    
    @ApiModelProperty(value = "资源描述")
    private String description;
    
    @ApiModelProperty(value = "资源URL")
    private String url;
    
    @ApiModelProperty(value = "资源分类")
    private String category;
}