package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.dto.CheckTaskResultDTO;
import com.abin.checkrepeatservice.student.service.CheckTaskService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师端查重任务控制器：仅教师角色可访问
 */
@RestController
@RequestMapping("/api/teacher/check-tasks")
@PreAuthorize("hasAuthority('TEACHER')") // 权限控制：仅教师可访问
public class TeacherCheckTaskController {

    @Resource
    private CheckTaskService checkTaskService;

    /**
     * 1. 教师查询自己指导学生的查重任务列表
     * @param paperId 论文ID（可选）
     * @param checkStatus 任务状态（可选）
     */
    @GetMapping("/list")
    public Result<List<CheckTaskResultDTO>> getGuideCheckTaskList(
            @RequestParam(required = false) Long paperId,
            @RequestParam(required = false) Integer checkStatus) {
        return checkTaskService.getMyCheckTaskList(paperId, checkStatus);
    }

    /**
     * 2. 教师查询查重任务详情（仅自己指导的学生）
     * @param taskId 任务ID
     */
    @GetMapping("/{taskId}")
    public Result<CheckTaskResultDTO> getCheckTaskDetail(@PathVariable Long taskId) {
        return checkTaskService.getCheckTaskDetail(taskId);
    }
}
