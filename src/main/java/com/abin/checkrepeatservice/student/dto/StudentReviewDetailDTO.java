package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生端审核结果详情DTO：简化展示，聚焦学生关注的信息
 */
@Data
public class StudentReviewDetailDTO {
    /**
     * 论文基础信息
     */
    private PaperBasicDTO paperBasic;

    /**
     * 查重核心结果
     */
    private CheckCoreDTO checkCore;

    /**
     * 审核结果信息
     */
    private ReviewResultDTO reviewResult;

    /**
     * 重新提交记录（可选，若有多次提交）
     */
    private LatestResubmitDTO latestResubmit;

    // ------------------------------ 内部DTO ------------------------------
    /**
     * 论文基础信息（简化）
     */
    @Data
    public static class PaperBasicDTO {
        private Long paperId;
        private String paperTitle;
        private String submitTime; // 格式：yyyy-MM-dd HH:mm:ss
        private String paperStatusDesc; // 状态描述：待审核/审核通过/审核不通过
        private String teacherName; // 指导教师姓名
    }

    /**
     * 查重核心结果（简化，仅展示关键信息）
     */
    @Data
    public static class CheckCoreDTO {
        private Double checkRate; // 重复率（如15.32%）
        private String checkTime; // 查重完成时间
        private String reportDownloadUrl; // 查重报告下载URL
    }

    /**
     * 审核结果信息（学生关注的意见与附件）
     */
    @Data
    public static class ReviewResultDTO {
        private String reviewStatusDesc; // 审核结果：通过/不通过
        private String reviewTime; // 审核时间
        private String reviewOpinion; // 审核意见（富文本，已清洗XSS）
        private ReviewAttachDTO attachInfo; // 审核附件（可选）
    }

    /**
     * 审核附件信息
     */
    @Data
    public static class ReviewAttachDTO {
        private String attachName; // 附件原文件名
        private Long attachSize; // 附件大小（单位：KB）
        private String downloadUrl; // 附件下载URL
    }

    /**
     * 最新重新提交记录
     */
    @Data
    public static class LatestResubmitDTO {
        private Long resubmitPaperId; // 重新提交的论文ID
        private String resubmitTime; // 重新提交时间
        private String revisionDesc; // 修改说明
        private String currentStatusDesc; // 当前状态（待查重/待审核）
    }
}
