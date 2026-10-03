package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.ReviewWorkflowUpdateReq;
import com.abin.checkrepeatservice.admin.service.ReviewWorkflowService;
import com.abin.checkrepeatservice.admin.vo.ReviewWorkflowVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审核工作流配置控制器。
 *
 * 挂在 /api/admin/** 下（与相似度阈值设置同款），理由：
 *   1. SecurityConfig 的 URL 规则会给未授权请求返回正确的 403；
 *      若只靠 @PreAuthorize，拒绝会被 GlobalExceptionHandler 的 Exception 兜底成 500。
 *   2. 超级管理员经 UserDetailsServiceImpl 额外持有 ADMIN 权限，无需单独放行。
 *
 * 原先页面调的是 /api/teacher/review-workflow：那是教师域的路径，而这是管理端页面，
 * 非 TEACHER 身份调用只会被 hasAuthority("TEACHER") 拒掉（表现为 401，见 SecurityConfig
 * 里 DispatcherType.ERROR 的注释）。这里换成管理域路径，权限与页面归属才对得上。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/review-workflow")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Api(tags = "审核工作流配置")
public class ReviewWorkflowController {

    private final ReviewWorkflowService reviewWorkflowService;

    /**
     * 获取审核工作流配置
     */
    @GetMapping
    @ApiOperation("获取审核工作流配置")
    public Result<ReviewWorkflowVO> getWorkflow() {
        try {
            return Result.success(reviewWorkflowService.getWorkflow());
        } catch (Exception e) {
            log.error("获取审核工作流配置失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取审核工作流配置失败");
        }
    }

    /**
     * 更新审核工作流配置（整体覆盖）
     */
    @PutMapping
    @ApiOperation("更新审核工作流配置")
    @OperationLog(type = "review_workflow_update", description = "更新审核工作流配置")
    public Result<Void> updateWorkflow(@RequestBody ReviewWorkflowUpdateReq req) {
        try {
            reviewWorkflowService.updateWorkflow(req);
            return Result.success("审核工作流配置更新成功");
        } catch (BusinessException be) {
            // 必须放行给 GlobalExceptionHandler，才能得到 400 + 自定义文案。
            // 落到下面的 catch (Exception) 会被吞成 500「系统繁忙」，
            // 把"步骤名称为空"这类参数错误伪装成服务故障。
            throw be;
        } catch (Exception e) {
            log.error("更新审核工作流配置失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新审核工作流配置失败");
        }
    }

    /**
     * 获取审批人候选列表（教师）
     */
    @GetMapping("/teachers")
    @ApiOperation("获取审批人候选教师列表")
    public Result<List<ReviewWorkflowVO.TeacherOption>> getTeachers() {
        try {
            return Result.success(reviewWorkflowService.listTeacherOptions());
        } catch (Exception e) {
            log.error("获取审批人候选列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取审批人列表失败");
        }
    }

}
