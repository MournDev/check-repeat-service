package com.abin.checkrepeatservice.student.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 *聊天记录导出请求DTO
 */
@Data
@ApiModel("聊天记录导出请求")
public class ChatExportDTO {

    @ApiModelProperty("会话ID")
    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    @ApiModelProperty("导出格式")
    @NotBlank(message = "导出格式不能为空")
    private String format = "pdf";

    @ApiModelProperty("开始时间")
    private String startTime;

    @ApiModelProperty("结束时间")
    private String endTime;
}