package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.student.vo.GuidanceRecordVO;
import com.abin.checkrepeatservice.teacher.dto.GuidanceCreateReq;
import com.abin.checkrepeatservice.teacher.service.TeacherGuidanceService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 教师端指导记录控制器（论文全生命周期 P1）：仅教师角色可访问
 */
@RestController
@RequestMapping("/api/teacher/guidance")
@PreAuthorize("hasAuthority('TEACHER')")
public class TeacherGuidanceController {

    @Resource
    private TeacherGuidanceService teacherGuidanceService;

    /**
     * 发起指导记录（学生侧进入待确认）
     */
    @PostMapping("/create")
    @OperationLog(type = "teacher_guidance_create", description = "教师发起指导记录")
    public Result<GuidanceRecordVO> createGuidance(@Valid @RequestBody GuidanceCreateReq req) {
        return teacherGuidanceService.createGuidance(req);
    }

    /**
     * 查询我发起的指导记录（分页）
     *
     * @param paperId     论文ID（可选过滤）
     * @param currentPage 当前页码（默认1）
     * @param pageSize    每页条数（默认10）
     */
    @GetMapping("/list")
    public Result<Page<GuidanceRecordVO>> listMyGuidance(
            @RequestParam(value = "paperId", required = false) Long paperId,
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return teacherGuidanceService.listMyGuidance(paperId, currentPage, pageSize);
    }
}
