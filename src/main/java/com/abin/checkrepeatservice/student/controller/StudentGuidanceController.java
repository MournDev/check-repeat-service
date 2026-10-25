package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.student.dto.GuidanceConfirmReq;
import com.abin.checkrepeatservice.student.service.StudentGuidanceService;
import com.abin.checkrepeatservice.student.vo.GuidanceRecordVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 学生端指导记录控制器（论文全生命周期 P1）：仅学生角色可访问
 */
@RestController
@RequestMapping("/api/student/guidance")
@PreAuthorize("hasAuthority('STUDENT')")
@Tag(name = "学生指导记录模块", description = "学生查询与确认导师指导记录接口")
public class StudentGuidanceController {

    @Resource
    private StudentGuidanceService studentGuidanceService;

    /**
     * 查询我的指导记录（分页）
     *
     * @param confirmStatus 确认状态（可选：0-待确认，1-已确认，2-有异议）
     * @param currentPage   当前页码（默认1）
     * @param pageSize      每页条数（默认10）
     */
    @GetMapping("/list")
    @Operation(summary = "查询我的指导记录", description = "分页查询本人指导记录")
    public Result<Page<GuidanceRecordVO>> listMyGuidance(
            @Parameter(description = "确认状态（可选：0-待确认，1-已确认，2-有异议）") @RequestParam(value = "confirmStatus", required = false) Integer confirmStatus,
            @Parameter(description = "当前页码") @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @Parameter(description = "每页条数") @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return studentGuidanceService.listMyGuidance(confirmStatus, currentPage, pageSize);
    }

    /**
     * 确认指导记录（仅本人、仅待确认状态；确认后固化并通知指导教师）
     */
    @PutMapping("/{recordId}/confirm")
    @Operation(summary = "确认指导记录", description = "仅本人、仅待确认状态；确认后固化并通知指导教师")
    @OperationLog(type = "student_guidance_confirm", description = "学生确认指导记录")
    public Result<Void> confirmRecord(
                                      @Parameter(description = "指导记录ID", required = true) @PathVariable("recordId") Long recordId,
                                      @Parameter(description = "指导记录确认请求", required = true) @Valid @RequestBody GuidanceConfirmReq req) {
        return studentGuidanceService.confirmRecord(recordId, req);
    }
}
