package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.WorkflowVoteReq;
import com.abin.checkrepeatservice.teacher.vo.WorkflowReviewVO;

import java.util.List;

/**
 * 多级初审服务（论文全生命周期 P2，ReviewWorkflow 配置壳接线）。
 *
 * 流程模型：导师初审（既有 doReview）通过后，若启用了工作流且配置了步骤，
 * 论文进入 steps 定义的多级初审链条，逐步推进；任一步骤驳回 → 论文整体 rejected；
 * 全部步骤通过 → 初审完成（completed）。未启用工作流时保持既有单轮审核行为。
 */
public interface TeacherWorkflowReviewService {

    /**
     * 我的初审工作流待办：我所在当前步骤的审批论文列表。
     * 含已投票项（前端据此禁用按钮）。
     */
    Result<List<WorkflowReviewVO>> listMyWorkflowTasks();

    /**
     * 对论文当前步骤投票（一人一步一票；all/any 语义由投票记录裁决）
     *
     * @param paperId 论文ID
     * @param req     投票结果与意见
     */
    Result<String> vote(Long paperId, WorkflowVoteReq req);
}
