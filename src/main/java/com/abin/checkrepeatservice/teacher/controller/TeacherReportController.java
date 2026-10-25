package com.abin.checkrepeatservice.teacher.controller;


import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.abin.checkrepeatservice.student.service.CheckReportService;
import com.abin.checkrepeatservice.student.vo.ReportDownloadReq;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 教师端查重报告控制器：仅教师角色可访问
 */
@RestController
@RequestMapping("/api/teacher/reports")
@PreAuthorize("hasAuthority('TEACHER')")
@Tag(name = "教师查重报告", description = "教师预览、下载与查询指导学生查重报告")
public class TeacherReportController {

    @Resource
    private CheckReportService checkReportService;

    /**
     * 1. 教师预览指导学生的报告详情
     * @param reportId 报告ID
     */
    @GetMapping("/preview")
    @Operation(summary = "预览查重报告", description = "教师预览指导学生的报告详情")
    public Result<ReportPreviewDTO> previewReport(
            @Parameter(description = "报告ID", required = true)
            @RequestParam Long reportId) {
        return checkReportService.previewReport(reportId);
    }

    /**
     * 2. 教师下载指导学生的报告文件
     * @param downloadReq 下载请求参数
     * @param response HTTP响应
     */
    @PostMapping("/download")
    @Operation(summary = "下载查重报告", description = "教师下载指导学生的报告文件")
    public void downloadReport(
            @Parameter(description = "报告下载请求参数", required = true)
            @Valid @RequestBody ReportDownloadReq downloadReq,
            HttpServletResponse response) {
        checkReportService.downloadReport(downloadReq, response);
    }

    /**
     * 3. 教师查询指导学生的历史报告列表
     * @param paperId 论文ID（可选）
     */
    @GetMapping("/list")
    @Operation(summary = "查询查重报告列表", description = "教师查询指导学生的历史报告列表，可按论文过滤")
    public Result<List<CheckReport>> getGuideReportList(
            @Parameter(description = "论文ID（可选）")
            @RequestParam(required = false) Long paperId) {
        return checkReportService.getMyReportList(paperId);
    }
}
