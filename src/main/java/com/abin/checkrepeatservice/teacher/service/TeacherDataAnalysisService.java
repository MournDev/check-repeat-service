package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.abin.checkrepeatservice.teacher.dto.TrendQueryDTO;
import com.abin.checkrepeatservice.teacher.dto.TrendResultDTO;
import com.abin.checkrepeatservice.teacher.vo.CollegeDistributionVO;
import com.abin.checkrepeatservice.teacher.vo.ReviewStatusVO;
import com.abin.checkrepeatservice.teacher.vo.SimilarityDistributionVO;
import com.abin.checkrepeatservice.teacher.vo.TrendDataVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

public interface TeacherDataAnalysisService {

    /**
     * 获取审核趋势数据
     */
    TrendResultDTO getReviewTrend(TrendQueryDTO query);

    /**
     * 获取审核统计数据
     */
    Map<String, Object> getReviewStats(Long teacherId, String timeRange);

    /**
     * 获取详细数据表格
     */
    PageResultVO<TrendDataVO> getDetailData(TrendQueryDTO query, Integer page, Integer size);

    /**
     * 导出数据
     */
    void exportTrendData(TrendQueryDTO query, HttpServletResponse response);

    /**
     * 获取审核状态分布数据
     */
    List<ReviewStatusVO> getReviewStatusDistribution(Long teacherId, String timeRange);

    /**
     * 获取相似度分布数据
     */
    List<SimilarityDistributionVO> getSimilarityDistribution(Long teacherId, String timeRange);

    /**
     * 获取学院分布数据
     */
    List<CollegeDistributionVO> getCollegeDistribution(Long teacherId, String timeRange);
}
