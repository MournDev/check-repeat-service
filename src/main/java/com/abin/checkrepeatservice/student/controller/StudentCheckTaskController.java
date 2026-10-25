package com.abin.checkrepeatservice.student.controller;


import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.student.dto.CheckTaskResultDTO;
import com.abin.checkrepeatservice.student.service.CheckTaskService;
import com.abin.checkrepeatservice.user.vo.CheckResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "学生查重任务模块", description = "学生发起、查询与取消查重任务接口")
public class StudentCheckTaskController {

    @Resource
    private CheckTaskService checkTaskService;

    /**
     * 1. 学生发起查重任务
     */
    @PostMapping("/create")
    @Operation(summary = "发起查重任务")
    public Result<CheckResultVO> createCheckTask(
            @Parameter(description = "论文ID", required = true) @Valid @RequestParam Long paperId) {
        return checkTaskService.createCheckTask(paperId);
    }

    /**
     * 2. 学生查询自己的查重任务列表
     * @param paperId 论文ID（可选）
     * @param checkStatus 任务状态（可选：0-待执行，1-执行中，2-执行成功，3-执行失败）
     */
    @GetMapping("/list")
    @Operation(summary = "查询我的查重任务列表", description = "分页查询本人查重任务")
    public Result<Page<CheckTaskResultDTO>> getMyCheckTaskList(
            @Parameter(description = "论文ID（可选）") @RequestParam(required = false) Long paperId,
            @Parameter(description = "任务状态（可选：0-待执行，1-执行中，2-执行成功，3-执行失败）") @RequestParam(required = false) Integer checkStatus,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        return checkTaskService.getMyCheckTaskList(paperId, checkStatus, page, size);
    }

    /**
     * 3. 学生查询查重任务详情
     * @param paperId 论文ID
     */
    @GetMapping("/taskDetail")
    @Operation(summary = "查询查重任务详情")
    public Result<CheckTaskResultDTO> getCheckTaskDetail(
            @Parameter(description = "论文ID", required = true) @RequestParam Long paperId) {
        return checkTaskService.getCheckTaskDetail(paperId);
    }

    /**
     * 4. 学生取消待执行的查重任务
     * @param taskId 任务ID
     */
    @DeleteMapping("/cancel")
    @Operation(summary = "取消待执行的查重任务")
    public Result<String> cancelCheckTask(
            @Parameter(description = "任务ID", required = true) @RequestParam Long taskId) {
        return checkTaskService.cancelCheckTask(taskId);
    }
}
