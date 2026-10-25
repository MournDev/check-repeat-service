package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.CollegeCompareVO;
import com.abin.checkrepeatservice.admin.dto.MonthlyTrendVO;
import com.abin.checkrepeatservice.admin.dto.SimilarityDistributionVO;
import com.abin.checkrepeatservice.admin.service.AdminStatReportService;
import com.abin.checkrepeatservice.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端全校统计报表控制器（论文全生命周期 P5）：仅管理员角色可访问。
 *
 * 路由前缀：/api/admin/stat-report/**（查重结果分布、按学院对比、月度趋势、报表导出）。
 * 全部为只读查询/导出，参照 AdminDefenseController 的导出接口不加 @OperationLog 的惯例。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/stat-report")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "全校统计报表", description = "查重结果分布/学院对比/月度趋势/报表导出")
public class AdminStatReportController {

    @Resource
    private AdminStatReportService adminStatReportService;

    /**
     * 查重结果分布（repeat_rate 十分桶计数，0-10/10-20/.../90-100）
     */
    @Operation(summary = "查询查重结果分布", description = "按 repeat_rate 十分桶统计查重结果分布")
    @GetMapping("/similarity-distribution")
    public Result<List<SimilarityDistributionVO>> getSimilarityDistribution() {
        return adminStatReportService.getSimilarityDistribution();
    }

    /**
     * 按学院统计（平均相似度 + 论文数，college_id 为 NULL 归"未分配"）
     */
    @Operation(summary = "按学院统计", description = "统计各学院平均相似度与论文数")
    @GetMapping("/college-compare")
    public Result<List<CollegeCompareVO>> getCollegeCompare() {
        return adminStatReportService.getCollegeCompare();
    }

    /**
     * 月度趋势（最近 12 个月查重次数与平均重复率）
     */
    @Operation(summary = "查询月度趋势", description = "统计最近 12 个月查重次数与平均重复率")
    @GetMapping("/monthly-trend")
    public Result<List<MonthlyTrendVO>> getMonthlyTrend() {
        return adminStatReportService.getMonthlyTrend();
    }

    /**
     * 导出报表（EasyExcel 一个 xlsx，含"查重结果分布/学院对比/月度趋势"三个 sheet）
     */
    @Operation(summary = "导出统计报表", description = "导出含查重结果分布/学院对比/月度趋势三个 sheet 的 xlsx")
    @GetMapping("/export")
    public void exportStatReport(HttpServletResponse response) {
        adminStatReportService.exportStatReport(response);
    }
}
