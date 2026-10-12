package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageItemVO;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageReq;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalStatsVO;
import com.abin.checkrepeatservice.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 管理端导师评价服务（论文全生命周期 P6a）。
 *
 * 管理端实名可见学生姓名（防滥用追溯）；统计维度：完成率 / 导师排行 / 学院对比 / 低分预警。
 */
public interface AdminAdvisorEvalService {

    /**
     * 评价实名分页（teacherId/collegeId/分数段过滤）
     */
    Result<Page<AdvisorEvalPageItemVO>> page(AdvisorEvalPageReq req);

    /**
     * 评价统计（完成率 + 导师排行 + 学院对比 + 低分预警名单）
     */
    Result<AdvisorEvalStatsVO> stats();

    /**
     * 导出全量评价明细 Excel（实名，单 sheet）
     */
    void export(HttpServletResponse response);
}
