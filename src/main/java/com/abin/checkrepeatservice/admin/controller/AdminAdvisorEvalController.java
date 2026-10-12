package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageItemVO;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageReq;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalStatsVO;
import com.abin.checkrepeatservice.admin.service.AdminAdvisorEvalService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端导师评价控制器（论文全生命周期 P6a）：管理员可见。
 *
 * 实名分页（含学生姓名，防滥用追溯）+ 统计（完成率/导师排行/学院对比/低分预警）+ Excel 导出。
 */
@RestController
@RequestMapping("/api/admin/advisor-evaluation")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminAdvisorEvalController {

    @Resource
    private AdminAdvisorEvalService adminAdvisorEvalService;

    /**
     * 评价实名分页（teacherId/collegeId/总体分数段过滤）
     */
    @GetMapping("/page")
    public Result<Page<AdvisorEvalPageItemVO>> page(
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(value = "teacherId", required = false) Long teacherId,
            @RequestParam(value = "collegeId", required = false) Long collegeId,
            @RequestParam(value = "minOverall", required = false) Integer minOverall,
            @RequestParam(value = "maxOverall", required = false) Integer maxOverall) {
        AdvisorEvalPageReq req = new AdvisorEvalPageReq();
        req.setPageNum(pageNum);
        req.setPageSize(pageSize);
        req.setTeacherId(teacherId);
        req.setCollegeId(collegeId);
        req.setMinOverall(minOverall);
        req.setMaxOverall(maxOverall);
        return adminAdvisorEvalService.page(req);
    }

    /**
     * 评价统计（完成率 + 导师排行 + 学院对比 + 低分预警名单）
     */
    @GetMapping("/stats")
    public Result<AdvisorEvalStatsVO> stats() {
        return adminAdvisorEvalService.stats();
    }

    /**
     * 导出全量评价明细 Excel（实名，单 sheet）
     */
    @GetMapping("/export")
    @OperationLog(type = "admin_advisor_eval_export", description = "管理员导出导师评价明细")
    public void export(HttpServletResponse response) {
        adminAdvisorEvalService.export(response);
    }
}
