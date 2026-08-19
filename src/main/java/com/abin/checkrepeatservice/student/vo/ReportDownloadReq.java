package com.abin.checkrepeatservice.student.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 报告下载请求DTO：指定报告ID与下载格式
 */
@Data
public class ReportDownloadReq {
    /**
     * 报告ID（必传）
     */
    @NotNull(message = "报告ID不能为空")
    private Long reportId;

    /**
     * 下载格式（默认pdf，支持pdf/html）
     */
    private String format = "pdf";
}