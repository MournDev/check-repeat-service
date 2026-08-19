package com.abin.checkrepeatservice.student.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 检查清单项DTO
 */
@Data
@ApiModel(description = "检查清单项DTO")
public class ChecklistItemDTO {
    
    @ApiModelProperty(value = "检查项ID")
    private Long itemId;
    
    @ApiModelProperty(value = "检查项文本")
    private String text;
    
    @ApiModelProperty(value = "是否已检查")
    private Boolean checked;
}