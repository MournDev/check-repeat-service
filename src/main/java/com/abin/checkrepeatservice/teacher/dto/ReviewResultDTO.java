package com.abin.checkrepeatservice.teacher.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核结果DTO：用于返回审核详情
 */
@Data
public class ReviewResultDTO {
    /**
     * 审核记录ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long reviewId;

    /**
     * 论文基础信息
     */
    private PaperBaseInfoDTO paperBaseInfo;

    /**
     * 查重任务信息
     */
    private CheckTaskBaseDTO taskBaseInfo;

    /**
     * 审核操作信息
     */
    private ReviewOperateInfoDTO reviewOperateInfo;

    /**
     * 审核附件信息（可选）
     */
    private ReviewAttachDTO reviewAttach;

    /**
     * 审核意见（列表回显用）。
     * 取数口径：优先 review_record.review_opinion（正式审核记录），
     * 无审核记录或意见为空时回退 paper_info.audit_comment（S12）——
     * TeacherDashboard 批量/单篇审核、管理员审核只落 audit_comment，不产生 review_record。
     * 教师端已审列表 AuditRecords.vue 审核意见列按本字段回显。
     */
    private String feedback;

    /**
     * Wave 3：最新签核是否合格（qualified=1）；未签核为 false。
     */
    private Boolean signoffQualified;

    /**
     * Wave 3：对应的 paper_check_signoff.id。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long signoffId;

    /**
     * Wave 3：是否允许批量审核（signoffQualified && paperStatus=auditing）。
     */
    private Boolean canBatchReview;

    // ------------------------------ 内部DTO ------------------------------
    /**
     * 论文基础信息
     */
    @Data
    public static class PaperBaseInfoDTO {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long paperId;
        private String paperTitle;
        private String studentName;
        private String college;
        private String email;
        private String studentNo; // 学生学号
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime submitTime;
        private String paperStatus; // 审核后的论文状态
    }

    /**
     * 查重任务基础信息
     */
    @Data
    public static class CheckTaskBaseDTO {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long taskId;
        private String taskNo;
        private Double checkRate; // 重复率
        private LocalDateTime checkEndTime;
        private String reportNo; // 报告编号
    }

    /**
     * 审核操作信息
     */
    @Data
    public static class ReviewOperateInfoDTO {
        private Integer reviewStatus; //3/ 4：审核通过/审核不通过
        private String reviewStatusDesc; // 状态描述（通过/不通过）
        private String reviewOpinion; // 审核意见（富文本）
        private String reviewerName; // 审核教师姓名
        private LocalDateTime reviewTime;
    }

    /**
     * 审核附件信息
     */
    @Data
    public static class ReviewAttachDTO {
        private String attachName; // 附件原文件名
        private String attachPath; // 附件存储路径
        private Long attachSize; // 附件大小（字节）
        private String attachType; // 附件类型（如pdf）
        private String downloadUrl; // 附件下载URL
    }
}
