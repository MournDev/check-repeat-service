package com.abin.checkrepeatservice.student.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 版本对比请求DTO
 */
@Data
@ApiModel(description = "版本对比请求DTO")
public class VersionCompareRequestDTO {
    
    @ApiModelProperty(value = "起始版本")
    @NotNull(message = "起始版本不能为空")
    private Integer fromVersion;
    
    @ApiModelProperty(value = "目标版本")
    @NotNull(message = "目标版本不能为空")
    private Integer toVersion;
}