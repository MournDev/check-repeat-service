package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminReportService;
import com.abin.checkrepeatservice.admin.vo.AdminReportDetailVO;
import com.abin.checkrepeatservice.admin.vo.AdminReportExportReq;
import com.abin.checkrepeatservice.admin.vo.AdminReportListItemVO;
import com.abin.checkrepeatservice.admin.vo.AdminReportStatsVO;
import com.abin.checkrepeatservice.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "查重报告管理", description = "全库查重报告分页/统计/详情与批量导出")
public class AdminReportController {

    @Resource
    private AdminReportService adminReportService;

    /**
     * 获取报告列表（分页）
     */
    @Operation(summary = "获取报告列表", description = "分页查询查重报告，支持标题、学生、查重状态与相似度筛选")
    @GetMapping("/list")
    public Result<Page<AdminReportListItemVO>> getReportList(
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "论文标题（可选，模糊查询）")
            @RequestParam(required = false) String paperTitle,
            @Parameter(description = "学生姓名（可选，模糊查询）")
            @RequestParam(required = false) String studentName,
            @Parameter(description = "查重状态（可选）")
            @RequestParam(required = false) String checkStatus,
            @Parameter(description = "最小相似度（可选）")
            @RequestParam(required = false) BigDecimal minSimilarity,
            @Parameter(description = "最大相似度（可选）")
            @RequestParam(required = false) BigDecimal maxSimilarity) {

        log.info("接收获取报告列表请求: page={}, size={}, paperTitle={}, studentName={}, checkStatus={}, minSimilarity={}, maxSimilarity={}",
                page, size, paperTitle, studentName, checkStatus, minSimilarity, maxSimilarity);
        return adminReportService.getReportList(page, size, paperTitle, studentName,
                checkStatus, minSimilarity, maxSimilarity);
    }

    /**
     * 获取报告统计（与列表同一组筛选条件，保证统计口径与列表一致）
     */
    @Operation(summary = "获取报告统计", description = "按与列表相同的筛选条件统计报告数据")
    @GetMapping("/stats")
    public Result<AdminReportStatsVO> getReportStats(
            @Parameter(description = "论文标题（可选，模糊查询）")
            @RequestParam(required = false) String paperTitle,
            @Parameter(description = "学生姓名（可选，模糊查询）")
            @RequestParam(required = false) String studentName,
            @Parameter(description = "查重状态（可选）")
            @RequestParam(required = false) String checkStatus,
            @Parameter(description = "最小相似度（可选）")
            @RequestParam(required = false) BigDecimal minSimilarity,
            @Parameter(description = "最大相似度（可选）")
            @RequestParam(required = false) BigDecimal maxSimilarity) {

        log.info("接收获取报告统计请求: paperTitle={}, studentName={}, checkStatus={}, minSimilarity={}, maxSimilarity={}",
                paperTitle, studentName, checkStatus, minSimilarity, maxSimilarity);
        return adminReportService.getReportStats(paperTitle, studentName, checkStatus,
                minSimilarity, maxSimilarity);
    }

    /**
     * 获取报告详情
     */
    @Operation(summary = "获取报告详情", description = "查询指定查重报告的详细信息")
    @GetMapping("/detail")
    public Result<AdminReportDetailVO> getReportDetail(
            @Parameter(description = "报告ID", required = true)
            @RequestParam Long reportId) {
        log.info("接收获取报告详情请求: reportId={}", reportId);
        return adminReportService.getReportDetail(reportId);
    }

    /**
     * 批量导出报告：1 份返回 PDF，多份返回 ZIP
     */
    @Operation(summary = "批量导出报告", description = "1 份返回 PDF，多份返回 ZIP")
    @PostMapping("/batch-export")
    public void batchExportReports(
            @Parameter(description = "批量导出请求体：ids 报告ID列表")
            @RequestBody(required = false) AdminReportExportReq req,
            HttpServletResponse response) {
        log.info("接收批量导出报告请求: ids={}", req == null ? null : req.getIds());
        adminReportService.batchExportReports(req == null ? null : req.getIds(), response);
    }
}
