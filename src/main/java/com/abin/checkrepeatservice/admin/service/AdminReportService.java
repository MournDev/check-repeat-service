package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.vo.AdminReportDetailVO;
import com.abin.checkrepeatservice.admin.vo.AdminReportListItemVO;
import com.abin.checkrepeatservice.admin.vo.AdminReportStatsVO;
import com.abin.checkrepeatservice.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理员报告管理服务
 * 职责：全库查重报告的分页查询、统计、详情查看与导出
 */
public interface AdminReportService {

    /**
     * 分页查询报告列表
     */
    Result<Page<AdminReportListItemVO>> getReportList(
            Integer page,
            Integer size,
            String paperTitle,
            String studentName,
            String checkStatus,
            BigDecimal minSimilarity,
            BigDecimal maxSimilarity);

    /**
     * 统计报告指标（与列表同一组筛选条件）
     */
    Result<AdminReportStatsVO> getReportStats(
            String paperTitle,
            String studentName,
            String checkStatus,
            BigDecimal minSimilarity,
            BigDecimal maxSimilarity);

    /**
     * 获取报告详情
     */
    Result<AdminReportDetailVO> getReportDetail(Long reportId);

    /**
     * 导出报告：单个返回PDF，多个返回ZIP
     * 直接向 response 写出字节流，不返回 Result
     */
    void batchExportReports(List<Long> ids, HttpServletResponse response);
}
