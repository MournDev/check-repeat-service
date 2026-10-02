package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminReportService;
import com.abin.checkrepeatservice.admin.vo.AdminReportDetailVO;
import com.abin.checkrepeatservice.admin.vo.AdminReportExportReq;
import com.abin.checkrepeatservice.admin.vo.AdminReportListItemVO;
import com.abin.checkrepeatservice.admin.vo.AdminReportStatsVO;
import com.abin.checkrepeatservice.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 管理员报告管理控制器
 * 职责：全库查重报告的分页查询、统计、详情查看与导出
 */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class AdminReportController {

    @Resource
    private AdminReportService adminReportService;

    /**
     * 获取报告列表（分页）
     */
    @GetMapping("/list")
    public Result<Page<AdminReportListItemVO>> getReportList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String paperTitle,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String checkStatus,
            @RequestParam(required = false) BigDecimal minSimilarity,
            @RequestParam(required = false) BigDecimal maxSimilarity) {

        log.info("接收获取报告列表请求: page={}, size={}, paperTitle={}, studentName={}, checkStatus={}, minSimilarity={}, maxSimilarity={}",
                page, size, paperTitle, studentName, checkStatus, minSimilarity, maxSimilarity);
        return adminReportService.getReportList(page, size, paperTitle, studentName,
                checkStatus, minSimilarity, maxSimilarity);
    }

    /**
     * 获取报告统计（与列表同一组筛选条件，保证统计口径与列表一致）
     */
    @GetMapping("/stats")
    public Result<AdminReportStatsVO> getReportStats(
            @RequestParam(required = false) String paperTitle,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String checkStatus,
            @RequestParam(required = false) BigDecimal minSimilarity,
            @RequestParam(required = false) BigDecimal maxSimilarity) {

        log.info("接收获取报告统计请求: paperTitle={}, studentName={}, checkStatus={}, minSimilarity={}, maxSimilarity={}",
                paperTitle, studentName, checkStatus, minSimilarity, maxSimilarity);
        return adminReportService.getReportStats(paperTitle, studentName, checkStatus,
                minSimilarity, maxSimilarity);
    }

    /**
     * 获取报告详情
     */
    @GetMapping("/detail")
    public Result<AdminReportDetailVO> getReportDetail(@RequestParam Long reportId) {
        log.info("接收获取报告详情请求: reportId={}", reportId);
        return adminReportService.getReportDetail(reportId);
    }

    /**
     * 批量导出报告：1 份返回 PDF，多份返回 ZIP
     */
    @PostMapping("/batch-export")
    public void batchExportReports(@RequestBody(required = false) AdminReportExportReq req,
                                   HttpServletResponse response) {
        log.info("接收批量导出报告请求: ids={}", req == null ? null : req.getIds());
        adminReportService.batchExportReports(req == null ? null : req.getIds(), response);
    }
}
