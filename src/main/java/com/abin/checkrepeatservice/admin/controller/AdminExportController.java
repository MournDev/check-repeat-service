package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminExportService;
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
public class AdminExportController {

    @Resource
    private AdminExportService adminExportService;

    @GetMapping("/reviews/export")
    public void exportReviews(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) {
        log.info("接收审核记录导出请求: semester={}, startDate={}, endDate={}", semester, startDate, endDate);
        adminExportService.exportReviews(semester, startDate, endDate, response);
    }

    @GetMapping("/defense/conclusions/export")
    public void exportDefenseConclusions(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) {
        log.info("接收答辩结论导出请求: semester={}, startDate={}, endDate={}", semester, startDate, endDate);
        adminExportService.exportDefenseConclusions(semester, startDate, endDate, response);
    }

    /**
     * Wave 3：学期数据包 ZIP（items=papers,checks,reviews,defense，默认全部）。
     */
    @GetMapping("/semester-package/export")
    public void exportSemesterPackage(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String items,
            HttpServletResponse response) {
        log.info("接收学期数据包导出请求: semester={}, items={}", semester, items);
        adminExportService.exportSemesterPackage(semester, startDate, endDate, items, response);
    }
}
