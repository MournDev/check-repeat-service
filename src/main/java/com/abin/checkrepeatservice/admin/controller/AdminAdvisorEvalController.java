package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageItemVO;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageReq;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalStatsVO;
import com.abin.checkrepeatservice.admin.service.AdminAdvisorEvalService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 管理端导师评价控制器（论文全生命周期 P6a）：管理员可见。
 *
 * 实名分页（含学生姓名，防滥用追溯）+ 统计（完成率/导师排行/学院对比/低分预警）+ Excel 导出。
 */
@RestController
@RequestMapping("/api/admin/advisor-evaluation")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "导师评价管理", description = "导师评价实名分页/统计与明细导出")
public class AdminAdvisorEvalController {

    @Resource
    private AdminAdvisorEvalService adminAdvisorEvalService;

    /**
     * 评价实名分页（teacherId/collegeId/总体分数段/评价时间段过滤）
     */
    @Operation(summary = "评价实名分页", description = "按导师、学院、总体分数段与评价时间段分页查询导师评价")
    @GetMapping("/page")
    public Result<Page<AdvisorEvalPageItemVO>> page(
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
            @Parameter(description = "导师ID（可选）")
            @RequestParam(value = "teacherId", required = false) Long teacherId,
            @Parameter(description = "学院ID（可选）")
            @RequestParam(value = "collegeId", required = false) Long collegeId,
            @Parameter(description = "总体分数下限（可选）")
            @RequestParam(value = "minOverall", required = false) Integer minOverall,
            @Parameter(description = "总体分数上限（可选）")
            @RequestParam(value = "maxOverall", required = false) Integer maxOverall,
            @Parameter(description = "评价时间起始日期（可选，格式 yyyy-MM-dd）")
            @RequestParam(value = "beginDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @Parameter(description = "评价时间结束日期（可选，格式 yyyy-MM-dd）")
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        AdvisorEvalPageReq req = new AdvisorEvalPageReq();
        req.setPageNum(pageNum);
        req.setPageSize(pageSize);
        req.setTeacherId(teacherId);
        req.setCollegeId(collegeId);
        req.setMinOverall(minOverall);
        req.setMaxOverall(maxOverall);
        req.setBeginDate(beginDate);
        req.setEndDate(endDate);
        return adminAdvisorEvalService.page(req);
    }

    /**
     * 评价统计（完成率 + 导师排行 + 学院对比 + 低分预警名单）。
     * beginDate/endDate 可选：按评价提交时间归口学年/届别，缺省为全量历史累计。
     */
    @Operation(summary = "评价统计", description = "统计完成率、导师排行、学院对比与低分预警名单")
    @GetMapping("/stats")
    public Result<AdvisorEvalStatsVO> stats(
            @Parameter(description = "评价时间起始日期（可选，格式 yyyy-MM-dd）")
            @RequestParam(value = "beginDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @Parameter(description = "评价时间结束日期（可选，格式 yyyy-MM-dd）")
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return adminAdvisorEvalService.stats(beginDate, endDate);
    }

    /**
     * 导出评价明细 Excel（实名，单 sheet）。
     * 过滤项与 /page 完全同口径（teacherId/collegeId/分数段/时间段），不传则导出全量，
     * 前端「导出明细」跟随当前列表筛选条件。
     */
    @Operation(summary = "导出评价明细", description = "按与分页相同的筛选条件导出实名评价明细 Excel")
    @GetMapping("/export")
    @OperationLog(type = "admin_advisor_eval_export", description = "管理员导出导师评价明细")
    public void export(HttpServletResponse response,
                       @Parameter(description = "导师ID（可选）")
                       @RequestParam(value = "teacherId", required = false) Long teacherId,
                       @Parameter(description = "学院ID（可选）")
                       @RequestParam(value = "collegeId", required = false) Long collegeId,
                       @Parameter(description = "总体分数下限（可选）")
                       @RequestParam(value = "minOverall", required = false) Integer minOverall,
                       @Parameter(description = "总体分数上限（可选）")
                       @RequestParam(value = "maxOverall", required = false) Integer maxOverall,
                       @Parameter(description = "评价时间起始日期（可选，格式 yyyy-MM-dd）")
                       @RequestParam(value = "beginDate", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
                       @Parameter(description = "评价时间结束日期（可选，格式 yyyy-MM-dd）")
                       @RequestParam(value = "endDate", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        AdvisorEvalPageReq req = new AdvisorEvalPageReq();
        req.setTeacherId(teacherId);
        req.setCollegeId(collegeId);
        req.setMinOverall(minOverall);
        req.setMaxOverall(maxOverall);
        req.setBeginDate(beginDate);
        req.setEndDate(endDate);
        adminAdvisorEvalService.export(req, response);
    }
}
