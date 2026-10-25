package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.dto.BatchCheckRequestDTO;
import com.abin.checkrepeatservice.student.dto.BatchCheckResultDTO;
import com.abin.checkrepeatservice.student.service.Impl.BatchCheckTaskServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 批量查重任务控制器
 * 
 * @author abin
 * @date 2026-03-05
 */
@RestController
@RequestMapping("/api/student/check-tasks")
@Tag(name = "批量查重任务模块", description = "批量创建查重任务接口")
public class BatchCheckTaskController {

    @Resource
    private BatchCheckTaskServiceImpl batchCheckTaskService;

    /**
     * 批量创建查重任务
     * 
     * @param request 批量请求参数（包含论文 ID 列表）
     * @return 批量结果（成功/失败列表、预估时间等）
     */
    @PostMapping("/batch-create")
    @Operation(summary = "批量创建查重任务", description = "根据论文ID列表批量创建查重任务")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<BatchCheckResultDTO> batchCreate(
            @Parameter(description = "批量请求参数（包含论文ID列表）", required = true) @RequestBody BatchCheckRequestDTO request) {
        return batchCheckTaskService.batchCreateCheckTasks(request);
    }
}
