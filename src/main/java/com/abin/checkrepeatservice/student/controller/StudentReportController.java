package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.abin.checkrepeatservice.student.service.CheckReportService;
import com.abin.checkrepeatservice.student.vo.ReportDownloadReq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 学生端查重报告控制器：仅学生角色可访问
 */
@RestController
@RequestMapping("/api/student/reports")
@Tag(name = "学生查重报告模块", description = "查重报告预览、下载与历史报告管理接口")
public class StudentReportController {

    @Resource
    private CheckReportService checkReportService;

    /**
     * 1. 学生预览报告详情
     * @param reportId 报告ID
     */
    @GetMapping("/preview")
    @Operation(summary = "预览报告详情")
    public Result<ReportPreviewDTO> previewReport(
            @Parameter(description = "报告ID", required = true) @RequestParam Long reportId) {
        return checkReportService.previewReport(reportId);
    }

    /**
     * 2. 学生下载报告文件
     * @param downloadReq 下载请求参数（reportId、format）
     * @param response HTTP响应（输出文件流）
     */
    @PostMapping("/download")
    @Operation(summary = "下载报告文件")
    public void downloadReport(
            @Parameter(description = "下载请求参数（reportId、format）", required = true) @Valid @RequestBody ReportDownloadReq downloadReq,
            HttpServletResponse response) {
        checkReportService.downloadReport(downloadReq, response);
    }

    /**
     * 3. 学生查询自己的历史报告列表
     * @param paperId 论文ID（可选，筛选指定论文的报告）
     */
    @GetMapping("/list")
    @Operation(summary = "查询我的历史报告列表", description = "可按论文筛选报告")
    public Result<List<CheckReport>> getMyReportList(
            @Parameter(description = "论文ID（可选，筛选指定论文的报告）") @RequestParam(required = false) Long paperId) {
        return checkReportService.getMyReportList(paperId);
    }

    /**
     * 4. 学生删除未审核的报告
     * @param reportId 报告ID
     */
    @DeleteMapping("/nocheck")
    @Operation(summary = "删除未审核的报告")
    public Result<String> deleteReport(
            @Parameter(description = "报告ID", required = true) @RequestParam Long reportId) {
        return checkReportService.deleteReport(reportId);
    }
}