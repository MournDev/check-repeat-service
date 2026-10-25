package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.student.dto.AdvisorEvalStatusVO;
import com.abin.checkrepeatservice.student.dto.AdvisorEvalSubmitReq;
import com.abin.checkrepeatservice.student.service.AdvisorEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端导师评价控制器（论文全生命周期 P6a）：仅学生角色可访问。
 *
 * 软提醒定位：终期答辩通过后邀请评价（通知 + 完成率统计），不卡定稿/归档阶段门。
 */
@RestController
@RequestMapping("/api/student/advisor-evaluation")
@PreAuthorize("hasAuthority('STUDENT')")
@Tag(name = "学生导师评价模块", description = "学生提交与查询导师评价接口")
public class StudentAdvisorEvalController {

    @Resource
    private AdvisorEvaluationService advisorEvaluationService;

    /**
     * 提交导师评价（每篇论文一次，提交后不可改）
     */
    @PostMapping
    @Operation(summary = "提交导师评价", description = "每篇论文一次，提交后不可修改")
    @OperationLog(type = "student_advisor_eval_submit", description = "学生提交导师评价")
    public Result<String> submit(
            @Parameter(description = "导师评价提交请求", required = true) @Valid @RequestBody AdvisorEvalSubmitReq req) {
        return advisorEvaluationService.submit(req);
    }

    /**
     * 我的导师评价状态（可评性/窗口/已提交回显/可选标签）
     *
     * @param paperId 论文ID，可空（空则取本人最新一篇论文）
     */
    @GetMapping("/my")
    @Operation(summary = "查询我的导师评价状态", description = "返回可评性、评价窗口、已提交回显与可选标签")
    public Result<AdvisorEvalStatusVO> my(
            @Parameter(description = "论文ID，可空（空则取本人最新一篇论文）") @RequestParam(value = "paperId", required = false) Long paperId) {
        return advisorEvaluationService.my(paperId);
    }
}
