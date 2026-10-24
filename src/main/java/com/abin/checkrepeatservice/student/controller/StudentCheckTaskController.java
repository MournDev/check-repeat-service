package com.abin.checkrepeatservice.student.controller;


import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.student.dto.CheckTaskResultDTO;
import com.abin.checkrepeatservice.student.service.CheckTaskService;
import com.abin.checkrepeatservice.user.vo.CheckResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生端查重任务控制器：仅学生角色可访问
 */
@RestController
@RequestMapping("/api/student/check-tasks")
public class StudentCheckTaskController {

    @Resource
    private CheckTaskService checkTaskService;

    /**
     * 1. 学生发起查重任务
     */
    @PostMapping("/create")
    public Result<CheckResultVO> createCheckTask(@Valid @RequestParam Long paperId) {
        return checkTaskService.createCheckTask(paperId);
    }

    /**
     * 2. 学生查询自己的查重任务列表
     * @param paperId 论文ID（可选）
     * @param checkStatus 任务状态（可选：0-待执行，1-执行中，2-执行成功，3-执行失败）
     */
    @GetMapping("/list")
    public Result<Page<CheckTaskResultDTO>> getMyCheckTaskList(
            @RequestParam(required = false) Long paperId,
            @RequestParam(required = false) Integer checkStatus,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return checkTaskService.getMyCheckTaskList(paperId, checkStatus, page, size);
    }

    /**
     * 3. 学生查询查重任务详情
     * @param paperId 论文ID
     */
    @GetMapping("/taskDetail")
    public Result<CheckTaskResultDTO> getCheckTaskDetail(@RequestParam Long paperId) {
        return checkTaskService.getCheckTaskDetail(paperId);
    }

    /**
     * 4. 学生取消待执行的查重任务
     * @param taskId 任务ID
     */
    @DeleteMapping("/cancel")
    public Result<String> cancelCheckTask(@RequestParam Long taskId) {
        return checkTaskService.cancelCheckTask(taskId);
    }
}
