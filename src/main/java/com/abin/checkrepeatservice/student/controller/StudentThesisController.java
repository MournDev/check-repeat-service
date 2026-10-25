package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.service.StudentThesisProgressService;
import com.abin.checkrepeatservice.student.vo.ThesisTimelineVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "学生论文进度模块", description = "学生论文全生命周期进度查询接口")
public class StudentThesisController {

    @Resource
    private StudentThesisProgressService studentThesisProgressService;

    /**
     * 查询论文进度时间线（当前阶段 + 状态流转历史）
     *
     * @param paperId 论文ID（可选；不传时取当前学生最近提交的一篇论文）
     */
    @GetMapping("/timeline")
    @Operation(summary = "查询论文进度时间线", description = "返回当前阶段与状态流转历史")
    public Result<ThesisTimelineVO> getTimeline(
            @Parameter(description = "论文ID（可选；不传时取当前学生最近提交的一篇论文）") @RequestParam(value = "paperId", required = false) Long paperId) {
        return studentThesisProgressService.getTimeline(paperId);
    }
}
