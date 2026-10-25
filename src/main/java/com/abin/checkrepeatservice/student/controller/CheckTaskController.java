package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.student.service.CheckTaskService;
import com.abin.checkrepeatservice.user.vo.CheckResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/check")
@Tag(name = "查重任务模块", description = "创建查重任务/查询查重结果")
public class CheckTaskController {
    @Resource
    private CheckTaskService checkTaskService;

    /**
     * 创建查重任务
     */
    @PostMapping("/create")
    @Operation(summary = "创建查重任务", description = "根据论文ID异步发起查重，仅学生角色可调用")
    public Result<CheckResultVO> createCheckTask(
            @Parameter(description = "论文ID", required = true) @RequestParam Long paperId) {
        return checkTaskService.createCheckTask(paperId);
    }

    /**
     * 查询查重结果
     */
    @GetMapping("/result")
    @Operation(summary = "查询查重结果", description = "查询指定论文的查重任务结果")
    public Result<CheckResultVO> getCheckResult(
            @Parameter(description = "论文ID", required = true) @RequestParam Long paperId) {
        CheckResultVO resultVO = checkTaskService.getCheckResult(paperId);
        return Result.success("查询成功", resultVO);
    }
}
