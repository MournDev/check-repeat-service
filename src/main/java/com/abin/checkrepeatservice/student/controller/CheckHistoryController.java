package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.student.dto.*;
import com.abin.checkrepeatservice.student.service.PaperInfoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 学生查重历史控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/student/papers")
@Api(tags = "学生查重历史管理")
@Tag(name = "学生查重历史模块", description = "论文查重历史、相似度趋势与版本对比分析接口")
public class CheckHistoryController {

    @Resource
    private PaperInfoService paperInfoService;

    /**
     * 获取查重历史记录
     */
    @GetMapping("/{paperId}/check-history")
    @Operation(summary = "获取论文查重历史记录")
    @ApiOperation("获取论文查重历史记录")
    public Result<CheckHistoryResponseDTO> getCheckHistory(
            @Parameter(description = "论文ID", required = true) @PathVariable Long paperId) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        CheckHistoryResponseDTO history = paperInfoService.getCheckHistory(paperId, studentId);
        return Result.success("获取查重历史记录成功", history);
    }

    /**
     * 获取相似度趋势数据
     */
    @GetMapping("/{paperId}/similarity-trend")
    @Operation(summary = "获取相似度趋势数据")
    @ApiOperation("获取相似度趋势数据")
    public Result<SimilarityTrendDTO> getSimilarityTrend(
            @Parameter(description = "论文ID", required = true) @PathVariable Long paperId,
            @Parameter(description = "统计周期（天）") @RequestParam(defaultValue = "30") Integer period) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        SimilarityTrendDTO trend = paperInfoService.getSimilarityTrend(paperId, studentId, period);
        return Result.success("获取相似度趋势数据成功", trend);
    }

    /**
     * 版本对比分析
     */
    @PostMapping("/{paperId}/compare-versions")
    @Operation(summary = "版本对比分析")
    @ApiOperation("版本对比分析")
    public Result<VersionCompareResponseDTO> compareVersions(
            @Parameter(description = "论文ID", required = true) @PathVariable Long paperId,
            @Parameter(description = "版本对比请求参数", required = true) @Valid @RequestBody VersionCompareRequestDTO request) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        VersionCompareResponseDTO comparison = paperInfoService.compareVersions(paperId, studentId, request);
        return Result.success("版本对比分析成功", comparison);
    }

    /**
     * 获取论文统计分析数据
     */
    @GetMapping("/{paperId}/statistics")
    @Operation(summary = "获取论文统计分析数据")
    @ApiOperation("获取论文统计分析数据")
    public Result<StatisticsDTO> getPaperStatistics(
            @Parameter(description = "论文ID", required = true) @PathVariable Long paperId) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        StatisticsDTO statistics = paperInfoService.getPaperStatistics(paperId, studentId);
        return Result.success("获取统计分析数据成功", statistics);
    }
}