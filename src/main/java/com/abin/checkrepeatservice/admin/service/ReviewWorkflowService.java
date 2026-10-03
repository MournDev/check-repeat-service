package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.ReviewWorkflowUpdateReq;
import com.abin.checkrepeatservice.admin.vo.ReviewWorkflowVO;

import java.util.List;

/**
 * 审核工作流配置服务。
 */
public interface ReviewWorkflowService {

    /**
     * 读取审核工作流配置。从未配置过时返回一套空配置，而不是抛错。
     */
    ReviewWorkflowVO getWorkflow();

    /**
     * 整体覆盖审核工作流配置
     */
    void updateWorkflow(ReviewWorkflowUpdateReq req);

    /**
     * 审批人下拉候选项（教师列表）
     */
    List<ReviewWorkflowVO.TeacherOption> listTeacherOptions();

}
