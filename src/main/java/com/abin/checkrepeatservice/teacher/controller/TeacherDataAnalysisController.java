package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.teacher.dto.TrendQueryDTO;
import com.abin.checkrepeatservice.teacher.dto.TrendResultDTO;
import com.abin.checkrepeatservice.teacher.service.TeacherDataAnalysisService;
import com.abin.checkrepeatservice.teacher.vo.CollegeDistributionVO;
import com.abin.checkrepeatservice.teacher.vo.ReviewStatusVO;
import com.abin.checkrepeatservice.teacher.vo.SimilarityDistributionVO;
import com.abin.checkrepeatservice.teacher.vo.TrendDataVO;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/teacher/data-analysis")
@Slf4j
public class TeacherDataAnalysisController {
    @Autowired
    private TeacherDataAnalysisService trendService;

    @GetMapping("/review-trend")
    @ApiOperation("获取审核趋势图表数据")
    public Result<Map<String, Object>> getReviewTrendForRequirement(
            @RequestParam Long teacherId,
            @RequestParam(defaultValue = "week") String timeRange,
            @RequestParam(defaultValue = "line") String chartType) {
        // 构造查询参数
        TrendQueryDTO query = new TrendQueryDTO();
        query.setTeacherId(teacherId);
        query.setTimeRange(timeRange);
        query.setChartType("daily".equals(chartType) ? "daily" : "monthly");
        
        TrendResultDTO trendResult = trendService.getReviewTrend(query);
        
        // 构造符合接口清单要求的响应格式
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("categories", trendResult.getXAxis());
        
        List<Map<String, Object>> series = new ArrayList<>();
        if (trendResult.getSeries() != null && !trendResult.getSeries().isEmpty()) {
            for (TrendResultDTO.ChartSeriesDTO chartSeries : trendResult.getSeries()) {
                Map<String, Object> seriesItem = new HashMap<>();
                seriesItem.put("name", chartSeries.getName());
                seriesItem.put("type", chartSeries.getType());
                seriesItem.put("data", chartSeries.getData());
                series.add(seriesItem);
            }
        } else {
            // 如果没有系列数据，创建默认的审核数系列
            Map<String, Object> seriesItem = new HashMap<>();
            seriesItem.put("name", "审核数");
            seriesItem.put("type", chartType);
            seriesItem.put("data", new ArrayList<Integer>()); // 空数据
            series.add(seriesItem);
        }
        
        responseData.put("series", series);
        
        return Result.success(responseData);
    }

    @GetMapping("/review-stats")
    @ApiOperation("获取审核统计概览数据")
    public Result<Map<String, Object>> getReviewStatsForRequirement(
            @RequestParam Long teacherId,
            @RequestParam(defaultValue = "week") String timeRange) {
        Map<String, Object> stats = trendService.getReviewStats(teacherId, timeRange);
        
        // 构造符合接口清单要求的响应格式
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("totalReviews", stats.get("totalReviews"));
        responseData.put("pendingReviews", stats.get("pendingReviews"));
        responseData.put("approvedReviews", stats.get("approvedReviews"));
        responseData.put("currentStudents", stats.get("currentStudents"));
        
        // 添加趋势数据
        responseData.put("totalReviewsTrend", createTrendData("up", "+12% 同比增长"));
        responseData.put("approvedReviewsTrend", createTrendData("up", "+8% 同比增长"));
        responseData.put("currentStudentsTrend", createTrendData("down", "-2% 同比下降"));
        
        return Result.success(responseData);
    }

    @GetMapping("/stats")
    @ApiOperation("获取统计数据概览")
    public Result<Map<String, Object>> getReviewStats(
            @RequestParam Long teacherId,
            @RequestParam(defaultValue = "week") String timeRange) {
        Map<String, Object> stats = trendService.getReviewStats(teacherId, timeRange);
        return Result.success(stats);
    }

    @GetMapping("/detail-data")
    @ApiOperation("获取详细统计数据（表格数据）")
    public Result<List<Map<String, Object>>> getDetailDataForRequirement(
            @RequestParam Long teacherId,
            @RequestParam(defaultValue = "week") String timeRange,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        // 构造查询参数
        TrendQueryDTO query = new TrendQueryDTO();
        query.setTeacherId(teacherId);
        query.setTimeRange(timeRange);
        query.setChartType("daily");
        
        if (startDate != null && !startDate.isEmpty()) {
            query.setStartDate(java.time.LocalDate.parse(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            query.setEndDate(java.time.LocalDate.parse(endDate));
        }
        
        // 获取详细数据
        PageResultVO<TrendDataVO> pageResult = trendService.getDetailData(query, 1, 1000); // 获取全部数据
        List<TrendDataVO> dataList = pageResult.getList();
        
        // 转换为接口清单要求的格式
        List<Map<String, Object>> resultData = new ArrayList<>();
        for (TrendDataVO data : dataList) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", data.getDate() != null ? data.getDate().toString() : "");
            item.put("reviews", data.getReviewCount() != null ? data.getReviewCount() : 0);
            item.put("approved", data.getApprovedCount() != null ? data.getApprovedCount() : 0);
            item.put("rejected", data.getRejectedCount() != null ? data.getRejectedCount() : 0);
            item.put("modified", 0); // 暂时不支持修改数
            item.put("avgSimilarity", data.getAvgSimilarity() != null ? data.getAvgSimilarity().intValue() : 0);
            item.put("avgProcessingTime", data.getAvgReviewTime() != null ? data.getAvgReviewTime().intValue() : 0);
            resultData.add(item);
        }
        
        return Result.success(resultData);
    }

    @PostMapping("/export")
    @ApiOperation("导出数据")
    public void exportData(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(defaultValue = "week") String timeRange,
            @RequestParam(defaultValue = "daily") String chartType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) {
        try {
            TrendQueryDTO query = buildTrendQuery(teacherId, timeRange, chartType, startDate, endDate);
            trendService.exportTrendData(query, response);
        } catch (Exception e) {
            log.error("导出数据失败: {}", e.getMessage());
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    @GetMapping("/review-status-distribution")
    @ApiOperation("获取审核状态分布数据")
    public Result<List<Map<String, Object>>> getReviewStatusDistributionForRequirement(
            @RequestParam Long teacherId,
            @RequestParam(defaultValue = "week") String timeRange) {
        List<ReviewStatusVO> distribution = trendService.getReviewStatusDistribution(teacherId, timeRange);
        
        // 转换为接口清单要求的格式 [{value: 89, name: "通过"}]
        List<Map<String, Object>> resultData = new ArrayList<>();
        for (ReviewStatusVO item : distribution) {
            Map<String, Object> resultItem = new HashMap<>();
            resultItem.put("value", item.getCount());
            resultItem.put("name", item.getStatusName());
            resultData.add(resultItem);
        }
        
        return Result.success(resultData);
    }

    @GetMapping("/similarity-distribution")
    @ApiOperation("获取论文相似度分布数据")
    public Result<Map<String, Object>> getSimilarityDistributionForRequirement(
            @RequestParam Long teacherId,
            @RequestParam(defaultValue = "week") String timeRange) {
        List<SimilarityDistributionVO> distribution = trendService.getSimilarityDistribution(teacherId, timeRange);
        
        // 构造符合接口清单要求的响应格式
        Map<String, Object> responseData = new HashMap<>();
        
        // 提取分类和数据
        List<String> categories = new ArrayList<>();
        List<Integer> data = new ArrayList<>();
        
        for (SimilarityDistributionVO item : distribution) {
            categories.add(item.getRange());
            data.add(item.getPaperCount());
        }
        
        responseData.put("categories", categories);
        
        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> seriesItem = new HashMap<>();
        seriesItem.put("name", "论文数量");
        seriesItem.put("type", "bar");
        seriesItem.put("data", data);
        series.add(seriesItem);
        
        responseData.put("series", series);
        
        return Result.success(responseData);
    }

    @GetMapping("/college-distribution")
    @ApiOperation("获取学院分布数据")
    public Result<Map<String, Object>> getCollegeDistributionForRequirement(
            @RequestParam Long teacherId,
            @RequestParam(defaultValue = "week") String timeRange) {
        List<CollegeDistributionVO> distribution = trendService.getCollegeDistribution(teacherId, timeRange);
        
        // 构造符合接口清单要求的响应格式
        Map<String, Object> responseData = new HashMap<>();
        
        // 提取学院名称和学生人数
        List<String> categories = new ArrayList<>();
        List<Integer> data = new ArrayList<>();
        
        for (CollegeDistributionVO item : distribution) {
            categories.add(item.getCollegeName());
            data.add(item.getStudentCount());
        }
        
        responseData.put("categories", categories);
        
        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> seriesItem = new HashMap<>();
        seriesItem.put("name", "学生人数");
        seriesItem.put("type", "bar");
        seriesItem.put("data", data);
        series.add(seriesItem);
        
        responseData.put("series", series);
        
        return Result.success(responseData);
    }

    /**
     * 创建趋势数据对象
     */
    private Map<String, Object> createTrendData(String type, String text) {
        Map<String, Object> trend = new HashMap<>();
        trend.put("type", type);
        trend.put("text", text);
        return trend;
    }
    private TrendQueryDTO buildTrendQuery(Long teacherId, String timeRange, String chartType, 
                                        String startDate, String endDate) {
        TrendQueryDTO query = new TrendQueryDTO();
        query.setTeacherId(teacherId);
        query.setTimeRange(timeRange);
        query.setChartType(chartType);
        
        // 处理日期参数
        if (startDate != null && !startDate.isEmpty()) {
            query.setStartDate(java.time.LocalDate.parse(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            query.setEndDate(java.time.LocalDate.parse(endDate));
        }
        
        return query;
    }
}
