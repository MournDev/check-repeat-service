package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageItemVO;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageReq;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalStatsVO;
import com.abin.checkrepeatservice.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;

/**
 * 管理端导师评价服务（论文全生命周期 P6a）。
 *
 * 管理端实名可见学生姓名（防滥用追溯）；统计维度：完成率 / 导师排行 / 学院对比 / 低分预警。
 * P6b 起支持按评价提交时间段过滤（学年/届别归口），缺省全量历史累计。
 */
public interface AdminAdvisorEvalService {

    /**
     * 评价实名分页（teacherId/collegeId/分数段/评价时间段过滤）
     */
    Result<Page<AdvisorEvalPageItemVO>> page(AdvisorEvalPageReq req);

    /**
     * 评价统计（完成率 + 导师排行 + 学院对比 + 低分预警名单）。
     * 时间段非空时：评价数按提交时间过滤，可评论文数改按该时段内 defense_passed 状态日志口径。
     */
    Result<AdvisorEvalStatsVO> stats(LocalDate beginDate, LocalDate endDate);

    /**
     * 导出评价明细 Excel（实名，单 sheet）。
     * req 仅用过滤项（teacherId/collegeId/分数段/时间段），与分页完全同口径；分页字段由实现层覆盖为全量。
     */
    void export(AdvisorEvalPageReq req, HttpServletResponse response);
}
