package com.abin.checkrepeatservice.user.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 查重结果返回VO（前端展示专用）
 */
@Data
public class CheckResultVO {
    private Long taskId; // 查重任务ID
    private Long paperId; // 论文ID
    private String subjectName;// 学科名称
    private String paperTitle; // 论文标题
    private BigDecimal similarity; // 相似度（百分比）
    private String keywords;// 关键词
    private String checkSource; // 查重来源（本地库/第三方）
    private String reportUrl; // 查重报告链接（本地库可为空）
    private String mostSimilarPaper; // 最相似论文标题（本地库专用）
    private String checkStatus; // 查重状态：SUCCESS/FAIL
    private String failReason; // 失败原因（仅失败时返回）
    private LocalDateTime createTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long reportId;
    private String reportNo;
    private String reportPath;
}
