package com.abin.checkrepeatservice.teacher.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 多级初审待办 VO（论文全生命周期 P2，ReviewWorkflow 接线）。
 *
 * 返回「我所在的当前步骤、我是否已投票」，供教师端多级初审待办页渲染。
 */
@Data
public class WorkflowReviewVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    /**
     * 论文标题
     */
    private String paperTitle;

    /**
     * 学生姓名
     */
    private String studentName;

    /**
     * 指导教师姓名
     */
    private String teacherName;

    /**
     * 当前步骤序号（1-based）
     */
    private Integer stepIndex;

    /**
     * 当前步骤名称（来自 review_workflow.steps 配置）
     */
    private String stepName;

    /**
     * 审批方式：all-全部通过 / any-任一通过
     */
    private String approvalType;

    /**
     * 当前步骤全部审批人姓名（逗号分隔展示用）
     */
    private String approverNames;

    /**
     * 我在该步骤是否已投票
     */
    private Boolean myVoted;

    /**
     * 我的投票结果：1-通过 / 2-驳回（未投票为 null）
     */
    private Integer myVoteResult;

    /**
     * 进入当前步骤的时间（paper_info.update_time，近似值，用于超时提醒展示）
     */
    private LocalDateTime stepEnterTime;
}
