package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.student.dto.AdvisorEvalStatusVO;
import com.abin.checkrepeatservice.student.dto.AdvisorEvalSubmitReq;
import com.abin.checkrepeatservice.student.service.AdvisorEvaluationService;
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
public class StudentAdvisorEvalController {

    @Resource
    private AdvisorEvaluationService advisorEvaluationService;

    /**
     * 提交导师评价（每篇论文一次，提交后不可改）
     */
    @PostMapping
    @OperationLog(type = "student_advisor_eval_submit", description = "学生提交导师评价")
    public Result<String> submit(@Valid @RequestBody AdvisorEvalSubmitReq req) {
        return advisorEvaluationService.submit(req);
    }

    /**
     * 我的导师评价状态（可评性/窗口/已提交回显/可选标签）
     *
     * @param paperId 论文ID，可空（空则取本人最新一篇论文）
     */
    @GetMapping("/my")
    public Result<AdvisorEvalStatusVO> my(@RequestParam(value = "paperId", required = false) Long paperId) {
        return advisorEvaluationService.my(paperId);
    }
}
