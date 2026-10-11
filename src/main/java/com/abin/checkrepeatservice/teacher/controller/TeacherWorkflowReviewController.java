package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.teacher.dto.WorkflowVoteReq;
import com.abin.checkrepeatservice.teacher.service.TeacherWorkflowReviewService;
import com.abin.checkrepeatservice.teacher.vo.WorkflowReviewVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师多级初审控制器（论文全生命周期 P2，ReviewWorkflow 接线）：仅教师角色可访问。
 */
@RestController
@RequestMapping("/api/teacher/workflow-review")
@PreAuthorize("hasAuthority('TEACHER')")
public class TeacherWorkflowReviewController {

    @Resource
    private TeacherWorkflowReviewService teacherWorkflowReviewService;

    /**
     * 我的初审工作流待办（我所在当前步骤的审批论文，含已投票项）
     */
    @GetMapping("/pending")
    public Result<List<WorkflowReviewVO>> listMyWorkflowTasks() {
        return teacherWorkflowReviewService.listMyWorkflowTasks();
    }

    /**
     * 对论文当前步骤投票（一人一步一票；all/any 语义由后端裁决）
     */
    @PostMapping("/{paperId}/vote")
    @OperationLog(type = "teacher_workflow_vote", description = "教师多级初审投票")
    public Result<String> vote(@PathVariable("paperId") Long paperId,
                               @Valid @RequestBody WorkflowVoteReq req) {
        return teacherWorkflowReviewService.vote(paperId, req);
    }
}
