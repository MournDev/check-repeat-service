package com.abin.checkrepeatservice.admin.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Wave 3：管理端学期数据包导出（审核记录 / 答辩结论）。
 */
public interface AdminExportService {

    /**
     * 导出审核记录（review_record，按 review_time 过滤）。
     */
    void exportReviews(String semester, String startDate, String endDate, HttpServletResponse response);

    /**
     * 导出全校答辩结论（defense_record，按 defense_batch.defense_date 过滤）。
     */
    void exportDefenseConclusions(String semester, String startDate, String endDate, HttpServletResponse response);

    /**
     * Wave 3：学期数据包 ZIP（论文清单 / 查重统计 / 审核记录 / 答辩结论 + manifest.json）。
     *
     * @param items 逗号分隔：papers,checks,reviews,defense；为空表示全部
     */
    void exportSemesterPackage(String semester, String startDate, String endDate,
                               String items, HttpServletResponse response);
}
