package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.student.dto.GuidanceConfirmReq;
import com.abin.checkrepeatservice.student.service.StudentGuidanceService;
import com.abin.checkrepeatservice.student.vo.GuidanceRecordVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    public Result<Page<GuidanceRecordVO>> listMyGuidance(
            @RequestParam(value = "confirmStatus", required = false) Integer confirmStatus,
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return studentGuidanceService.listMyGuidance(confirmStatus, currentPage, pageSize);
    }

    /**
     * 确认指导记录（仅本人、仅待确认状态；确认后固化并通知指导教师）
     */
    @PutMapping("/{recordId}/confirm")
    @OperationLog(type = "student_guidance_confirm", description = "学生确认指导记录")
    public Result<Void> confirmRecord(@PathVariable("recordId") Long recordId,
                                      @Valid @RequestBody GuidanceConfirmReq req) {
        return studentGuidanceService.confirmRecord(recordId, req);
    }
}
