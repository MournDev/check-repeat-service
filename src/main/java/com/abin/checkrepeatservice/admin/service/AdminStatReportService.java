package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.CollegeCompareVO;
import com.abin.checkrepeatservice.admin.dto.MonthlyTrendVO;
import com.abin.checkrepeatservice.admin.dto.SimilarityDistributionVO;
import com.abin.checkrepeatservice.common.Result;
import jakarta.servlet.http.HttpServletResponse;

import java.io.OutputStream;
import java.util.List;

/**
 * 管理端全校统计报表服务（论文全生命周期 P5）。
 */
public interface AdminStatReportService {

    /**
     * 查重结果分布：check_result.repeat_rate 十分桶计数（缺失桶补 0）
     */
    Result<List<SimilarityDistributionVO>> getSimilarityDistribution();

    /**
     * 按学院统计：平均相似度 + 论文数（college_id 为 NULL 归"未分配"）
     */
    Result<List<CollegeCompareVO>> getCollegeCompare();

    /**
     * 月度趋势：最近 12 个月查重次数与平均重复率（缺失月补 0）
     */
    Result<List<MonthlyTrendVO>> getMonthlyTrend();

    /**
     * 导出三 sheet 报表（分布/学院对比/月度趋势）为一个 xlsx
     */
    void exportStatReport(HttpServletResponse response);

    /**
     * Wave 3：把三 sheet 统计报表写入任意输出流，返回总数据行数（学期包 ZIP 复用）。
     */
    int exportStatReportToStream(OutputStream outputStream);
}
