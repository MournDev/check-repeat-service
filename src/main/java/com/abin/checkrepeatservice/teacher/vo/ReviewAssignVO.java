package com.abin.checkrepeatservice.teacher.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评阅任务 VO（论文全生命周期 P2）。
 *
 * 教师端与管理端共用：
 *  - 教师端列表：reviewType=blind（盲审）时 studentName / teacherName 置 null（查询层脱敏）；
 *  - 管理端列表：不做脱敏，并额外返回 reviewerName。
 */
@Data
public class ReviewAssignVO {

    /**
     * 任务ID。雪花ID按字符串下发，防 JS 精度丢失（项目既有约定）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    /**
     * 论文标题
     */
    private String paperTitle;

    /**
     * 评阅针对的论文版本号
     */
    private Integer submitVersion;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long reviewerId;

    /**
     * 评阅教师姓名（仅管理端返回；教师端不回显自己姓名）
     */
    private String reviewerName;

    /**
     * 评阅类型：advisor / peer / blind
     */
    private String reviewType;

    private String reviewTypeDesc;

    /**
     * 评阅方式：online / offline
     */
    private String method;

    private String methodDesc;

    /**
     * 状态：0-待评阅 / 1-评阅中 / 2-已提交 / 3-已超时转派
     */
    private Integer status;

    private String statusDesc;

    private BigDecimal score;

    private String comment;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long attachmentId;

    /**
     * 评阅截止时间
     */
    private LocalDateTime deadline;

    /**
     * 分配方式：auto / manual
     */
    private String assignType;

    /**
     * 学生姓名（盲审时脱敏为 null）
     */
    private String studentName;

    /**
     * 指导教师姓名（盲审时脱敏为 null）
     */
    private String teacherName;

    /**
     * 任务下发时间
     */
    private LocalDateTime createTime;
}
