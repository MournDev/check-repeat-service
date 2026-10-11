package com.abin.checkrepeatservice.student.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指导记录响应 VO（论文全生命周期 P1，教师端与学生端共用）。
 * Long 型 ID 一律 @JsonFormat(STRING) 下发，避免雪花 ID 超出 JS 安全整数精度丢失。
 */
@Data
public class GuidanceRecordVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    /**
     * 论文标题
     */
    private String paperTitle;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long teacherId;

    /**
     * 指导教师姓名
     */
    private String teacherName;

    /**
     * 指导轮次（同论文内自增）
     */
    private Integer round;

    /**
     * 指导方式：online-线上 / offline-线下
     */
    private String guidanceType;

    /**
     * 指导时间
     */
    private LocalDateTime guidanceTime;

    /**
     * 指导内容（纯文本）
     */
    private String content;

    /**
     * 学生反馈（确认后存在）
     */
    private String studentFeedback;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long attachmentId;

    /**
     * 确认状态：0-待学生确认 / 1-已确认 / 2-有异议
     */
    private Integer confirmStatus;

    /**
     * 确认状态描述
     */
    private String confirmStatusDesc;

    /**
     * 学生确认时间
     */
    private LocalDateTime confirmTime;

    /**
     * 记录发起时间
     */
    private LocalDateTime createTime;
}
