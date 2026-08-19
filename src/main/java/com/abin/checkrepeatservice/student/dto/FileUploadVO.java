package com.abin.checkrepeatservice.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 *文件上传响应VO
 */
@Data
@ApiModel("文件上传响应")
public class FileUploadVO {

    @ApiModelProperty("文件 ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @NotNull(message = "文件 ID 不能为空")
    private Long id;

    @ApiModelProperty("文件名")
    private String name;

    @ApiModelProperty("文件大小")
    private Long size;

    @ApiModelProperty("文件类型")
    private String type;

    @ApiModelProperty("文件URL")
    private String url;

    @ApiModelProperty("上传时间")
    private String uploadTime;
}