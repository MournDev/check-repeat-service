package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.service.StudentThesisProgressService;
import com.abin.checkrepeatservice.student.vo.ThesisTimelineVO;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端论文进度控制器（论文全生命周期 P1）：仅学生角色可访问
 */
@RestController
@RequestMapping("/api/student/thesis")
@PreAuthorize("hasAuthority('STUDENT')")
public class StudentThesisController {

    @Resource
    private StudentThesisProgressService studentThesisProgressService;

    /**
     * 查询论文进度时间线（当前阶段 + 状态流转历史）
     *
     * @param paperId 论文ID（可选；不传时取当前学生最近提交的一篇论文）
     */
    @GetMapping("/timeline")
    public Result<ThesisTimelineVO> getTimeline(
            @RequestParam(value = "paperId", required = false) Long paperId) {
        return studentThesisProgressService.getTimeline(paperId);
    }
}
