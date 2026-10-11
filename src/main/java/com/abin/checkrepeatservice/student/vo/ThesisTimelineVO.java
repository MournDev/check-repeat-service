package com.abin.checkrepeatservice.student.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 论文进度时间线 VO（论文全生命周期 P1）。
 * 数据来源：paper_info（当前阶段）+ paper_status_log（阶段流转历史）。
 */
@Data
public class ThesisTimelineVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    /**
     * 论文标题
     */
    private String paperTitle;

    /**
     * 当前状态原始值（DictConstants.PaperStatus 字典值）
     */
    private String paperStatus;

    /**
     * 当前状态标签（如"待审核（导师初审）"）
     */
    private String paperStatusDesc;

    /**
     * 阶段流转历史（按时间正序）
     */
    private List<Node> nodes;

    /**
     * 单次状态流转节点
     */
    @Data
    public static class Node {

        /**
         * 变更前状态（原始值，历史数据可能是数字码，已尽力归一化）
         */
        private String oldStatus;

        /**
         * 变更前状态标签
         */
        private String oldStatusDesc;

        /**
         * 变更后状态（原始值）
         */
        private String newStatus;

        /**
         * 变更后状态标签
         */
        private String newStatusDesc;

        /**
         * 变更原因（如"导师审核通过"）
         */
        private String reason;

        /**
         * 变更时间
         */
        private LocalDateTime operateTime;
    }
}
