package com.abin.checkrepeatservice.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 查重任务结果DTO：用于返回任务详情+报告摘要
 */
@Data
public class CheckTaskResultDTO {
    /**
     * 任务ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long taskId;

    /**
     * 任务编号（如：CHECK20251108001）
     */
    private String taskNo;

    /**
     * 论文ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    /**
     * 论文版本号（M3.1：任务创建时快照的 paper_info.version；存量任务为 null）
     */
    private Integer paperVersion;

    /**
     * 论文提交记录ID（M3.1：对应 paper_submit.id；存量任务为 null）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long submitId;

    /**
     * 论文标题
     */
    private String paperTitle;

    /**
     * 查重规则名称（如：本科毕业论文查重规则）
     */
    private String ruleName;

    /**
     * 检测范围（M1.1：当前仅校内历史论文库，未接入外部查重数据库）
     */
    private String checkScope;

    /**
     * 任务状态（0-待执行，1-执行中，2-执行成功，3-执行失败）
     */
    private String checkStatus;

    /**
     * 重复率（如：15.32 → 15.32%）
     *
     * P5 起新任务语义升级为「总相似比」（非引用命中句字数/正文字数），
     * 存量任务保留旧口径（整篇最大余弦），不迁移。
     */
    private Double checkRate;

    /**
     * 单篇最大相似比（如：12.34 → 12.34%，P5 新指标；存量任务为 null）
     */
    private Double maxPerSource;

    /**
     * 引用率（如：3.21 → 3.21%，P5 新指标，引用命中不计入总相似比；存量任务为 null）
     */
    private Double citationRate;

    /**
     * 任务开始时间
     */
    private LocalDateTime startTime;

    /**
     * 任务结束时间
     */
    private LocalDateTime endTime;

    /**
     * 失败原因（仅状态为3时非空）
     */
    private String failReason;

    /**
     * 查重报告摘要（仅状态为2时非空）
     */
    private CheckReportSummaryDTO reportSummary;

    // ------------------------------ 报告摘要内部类 ------------------------------
    @Data
    public static class CheckReportSummaryDTO {
        /**
         * 报告ID
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long reportId;

        /**
         * 报告编号
         */
        private String reportNo;

        /**
         * 重复段落数量
         */
        private Integer repeatParagraphCount;

        /**
         * 报告下载URL（前端拼接域名访问）
         */
        private String reportDownloadUrl;
    }
}