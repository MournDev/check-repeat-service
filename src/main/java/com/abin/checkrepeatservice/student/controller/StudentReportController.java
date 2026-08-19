package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.abin.checkrepeatservice.student.service.CheckReportService;
import com.abin.checkrepeatservice.student.vo.ReportDownloadReq;
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
public class StudentReportController {

    @Resource
    private CheckReportService checkReportService;

    /**
     * 1. 学生预览报告详情
     * @param reportId 报告ID
     */
    @GetMapping("/preview")
    public Result<ReportPreviewDTO> previewReport(@RequestParam Long reportId) {
        return checkReportService.previewReport(reportId);
    }

    /**
     * 2. 学生下载报告文件
     * @param downloadReq 下载请求参数（reportId、format）
     * @param response HTTP响应（输出文件流）
     */
    @PostMapping("/download")
    public void downloadReport(
            @Valid @RequestBody ReportDownloadReq downloadReq,
            HttpServletResponse response) {
        checkReportService.downloadReport(downloadReq, response);
    }

    /**
     * 3. 学生查询自己的历史报告列表
     * @param paperId 论文ID（可选，筛选指定论文的报告）
     */
    @GetMapping("/list")
    public Result<List<CheckReport>> getMyReportList(
            @RequestParam(required = false) Long paperId) {
        return checkReportService.getMyReportList(paperId);
    }

    /**
     * 4. 学生删除未审核的报告
     * @param reportId 报告ID
     */
    @DeleteMapping("/nocheck")
    public Result<String> deleteReport(@RequestParam Long reportId) {
        return checkReportService.deleteReport(reportId);
    }
}