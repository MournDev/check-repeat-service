package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.student.dto.CheckTaskResultDTO;
import com.abin.checkrepeatservice.student.service.CheckTaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师端查重任务控制器：仅教师角色可访问
 */
@RestController
@RequestMapping("/api/teacher/check-tasks")
@PreAuthorize("hasAuthority('TEACHER')") // 权限控制：仅教师可访问
@Tag(name = "教师查重任务", description = "教师查询指导学生的查重任务")
public class TeacherCheckTaskController {

    @Resource
    private CheckTaskService checkTaskService;

    /**
     * 1. 教师查询自己指导学生的查重任务列表
     * @param paperId 论文ID（可选）
     * @param checkStatus 任务状态（可选）
     */
    @GetMapping("/list")
    @Operation(summary = "获取查重任务列表", description = "查询自己指导学生的查重任务，可按论文与状态过滤")
    public Result<Page<CheckTaskResultDTO>> getGuideCheckTaskList(
            @Parameter(description = "论文ID（可选）")
            @RequestParam(required = false) Long paperId,
            @Parameter(description = "任务状态（可选）")
            @RequestParam(required = false) Integer checkStatus,
            @Parameter(description = "页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size) {
        return checkTaskService.getMyCheckTaskList(paperId, checkStatus, page, size);
    }

    /**
     * 2. 教师查询查重任务详情（仅自己指导的学生）
     * @param taskId 任务ID
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取查重任务详情", description = "仅可查看自己指导学生的查重任务")
    public Result<CheckTaskResultDTO> getCheckTaskDetail(
            @Parameter(description = "任务ID", required = true)
            @PathVariable Long taskId) {
        return checkTaskService.getTeacherCheckTaskDetail(UserBusinessInfoUtils.getCurrentUserId(), taskId);
    }
}
