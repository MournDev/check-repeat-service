package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.teacher.dto.ReviewSubmitReq;
import com.abin.checkrepeatservice.teacher.service.TeacherReviewAssignService;
import com.abin.checkrepeatservice.teacher.vo.ReviewAssignVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 教师评阅工作台控制器（论文全生命周期 P2）：仅教师角色可访问。
 * 线上评阅直接提交评分与意见；线下评阅提交扫描件回填结果。
 */
@RestController
@RequestMapping("/api/teacher/review-assign")
@PreAuthorize("hasAuthority('TEACHER')")
@Tag(name = "教师评阅工作台", description = "评阅任务查询与评阅结果提交")
public class TeacherReviewAssignController {

    @Resource
    private TeacherReviewAssignService teacherReviewAssignService;

    /**
     * 我的评阅任务列表（分页）
     *
     * @param status 状态过滤（可选：0-待评阅 / 2-已提交 / 3-已超时转派）
     */
    @GetMapping("/pending")
    @Operation(summary = "获取我的评阅任务列表", description = "分页查询评阅任务，可按状态过滤")
    public Result<Page<ReviewAssignVO>> listMyAssignments(
            @Parameter(description = "状态过滤：0-待评阅 / 2-已提交 / 3-已超时转派")
            @RequestParam(value = "status", required = false) Integer status,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return teacherReviewAssignService.listMyAssignments(status, currentPage, pageSize);
    }

    /**
     * 提交评阅结果（线上：评分+意见必填；线下：扫描件必填）
     */
    @PostMapping("/{id}/submit")
    @Operation(summary = "提交评阅结果", description = "线上评阅提交评分与意见；线下评阅提交扫描件")
    @OperationLog(type = "teacher_review_assign_submit", description = "教师提交评阅结果")
    public Result<ReviewAssignVO> submit(
            @Parameter(description = "评阅任务ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "评阅结果请求体", required = true)
            @Valid @RequestBody ReviewSubmitReq req) {
        return teacherReviewAssignService.submit(id, req);
    }
}
