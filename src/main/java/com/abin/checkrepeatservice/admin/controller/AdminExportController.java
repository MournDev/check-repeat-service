package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wave 3：管理端学期数据包导出（审核记录 / 答辩结论）。
 *
 * <p>二进制接口：参数校验在写流之前完成；成功带 Content-Disposition。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "学期数据包导出", description = "审核记录/答辩结论/学期数据包导出")
public class AdminExportController {

    @Resource
    private AdminExportService adminExportService;

    @Operation(summary = "导出审核记录", description = "按学期或日期范围导出审核记录")
    @GetMapping("/reviews/export")
    public void exportReviews(
            @Parameter(description = "学期（可选）")
            @RequestParam(required = false) String semester,
            @Parameter(description = "开始日期（可选）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（可选）")
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) {
        log.info("接收审核记录导出请求: semester={}, startDate={}, endDate={}", semester, startDate, endDate);
        adminExportService.exportReviews(semester, startDate, endDate, response);
    }

    @Operation(summary = "导出答辩结论", description = "按学期或日期范围导出答辩结论")
    @GetMapping("/defense/conclusions/export")
    public void exportDefenseConclusions(
            @Parameter(description = "学期（可选）")
            @RequestParam(required = false) String semester,
            @Parameter(description = "开始日期（可选）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（可选）")
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) {
        log.info("接收答辩结论导出请求: semester={}, startDate={}, endDate={}", semester, startDate, endDate);
        adminExportService.exportDefenseConclusions(semester, startDate, endDate, response);
    }

    /**
     * Wave 3：学期数据包 ZIP（items=papers,checks,reviews,defense，默认全部）。
     */
    @Operation(summary = "导出学期数据包", description = "按学期打包导出论文/查重/评阅/答辩数据为 ZIP")
    @GetMapping("/semester-package/export")
    public void exportSemesterPackage(
            @Parameter(description = "学期（可选）")
            @RequestParam(required = false) String semester,
            @Parameter(description = "开始日期（可选）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（可选）")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "导出项：papers,checks,reviews,defense，默认全部")
            @RequestParam(required = false) String items,
            HttpServletResponse response) {
        log.info("接收学期数据包导出请求: semester={}, items={}", semester, items);
        adminExportService.exportSemesterPackage(semester, startDate, endDate, items, response);
    }
}
