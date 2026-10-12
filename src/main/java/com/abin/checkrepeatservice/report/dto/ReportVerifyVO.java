package com.abin.checkrepeatservice.report.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报告验真结果 VO（论文全生命周期 P5）
 *
 * 公开接口出参：只回元数据（标题打码），绝不回报告文件与论文正文。
 */
@Data
public class ReportVerifyVO {

    /**
     * 报告编号
     */
    private String reportNo;

    /**
     * 验真结果：true-编号存在且内容哈希比对一致
     */
    private Boolean valid;

    /**
     * 论文标题（打码：保留首尾各 2 字符）
     */
    private String paperTitle;

    /**
     * 总相似比（%）
     */
    private BigDecimal checkRate;

    /**
     * 单篇最大相似比（%）
     */
    private BigDecimal maxPerSource;

    /**
     * 引用率（%）
     */
    private BigDecimal citationRate;

    /**
     * 查重完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkTime;

    /**
     * 验真说明（valid=false 时的具体原因）
     */
    private String message;
}
