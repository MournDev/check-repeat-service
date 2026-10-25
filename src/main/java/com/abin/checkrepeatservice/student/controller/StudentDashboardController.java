package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.student.dto.*;
import com.abin.checkrepeatservice.student.service.Impl.StudentDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/student/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "学生工作台模块", description = "学生工作台统计、论文进度与数据导出接口")
public class StudentDashboardController {

    private final StudentDashboardService dashboardService;
    @Resource
    private JwtUtils JwtUtil;

    // 获取仪表盘统计数据
    @GetMapping("/stats")
    @Operation(summary = "获取仪表盘统计数据")
    public Result<StudentDashboardStatsDTO> getDashboardStats(
            @RequestHeader("Authorization") String token) throws Exception {
        Long studentId = JwtUtil.getUserIdFromToken(token);
        StudentDashboardStatsDTO stats = dashboardService.getDashboardStats(studentId);
        return Result.success(stats);
    }

    // 获取最新论文信息
    @GetMapping("/latest-paper")
    @Operation(summary = "获取最新论文信息")
    public Result<LatestPaperDTO> getLatestPaper(
            @RequestHeader("Authorization") String token) throws Exception {
        Long studentId = JwtUtil.getUserIdFromToken(token);
        LatestPaperDTO paper = dashboardService.getLatestPaper(studentId);
        return Result.success(paper);
    }

    // 获取导师信息
    @GetMapping("/advisor")
    @Operation(summary = "获取导师信息")
    public Result<AdvisorInfoDTO> getAdvisorInfo(
            @RequestHeader("Authorization") String token) throws Exception {
        Long studentId = JwtUtil.getUserIdFromToken(token);
        AdvisorInfoDTO advisor = dashboardService.getAdvisorInfo(studentId);
        return Result.success(advisor);
    }

    // 获取时间节点信息
    @GetMapping("/deadlines")
    @Operation(summary = "获取时间节点信息")
    public Result<DeadlinesDTO> getDeadlines(
            @RequestHeader(value = "Authorization", required = false) String token) {
        DeadlinesDTO deadlines = dashboardService.getDeadlines();
        return Result.success(deadlines);
    }

    // 获取能力评估雷达图数据
    @GetMapping("/ability-radar")
    @Operation(summary = "获取能力评估雷达图数据")
    public Result<AbilityRadarDTO> getAbilityRadar(
            @RequestHeader("Authorization") String token) throws Exception {
        Long studentId = JwtUtil.getUserIdFromToken(token);
        AbilityRadarDTO radar = dashboardService.getAbilityRadar(studentId);
        return Result.success(radar);
    }

    // 获取相似度变化趋势
    @GetMapping("/similarity-trend")
    @Operation(summary = "获取相似度变化趋势")
    public Result<SimilarityTrendDTO> getSimilarityTrend(
            @RequestHeader("Authorization") String token) throws Exception {
        Long studentId = JwtUtil.getUserIdFromToken(token);
        SimilarityTrendDTO trend = dashboardService.getSimilarityTrend(studentId);
        return Result.success(trend);
    }

    // 获取专业对比数据
    @GetMapping("/major-comparison")
    @Operation(summary = "获取专业对比数据")
    public Result<MajorComparisonDTO> getMajorComparison(
            @RequestHeader("Authorization") String token) throws Exception {
        Long studentId = JwtUtil.getUserIdFromToken(token);
        MajorComparisonDTO comparison = dashboardService.getMajorComparison(studentId);
        return Result.success(comparison);
    }

    // 获取待办事项列表
    @GetMapping("/todo-list")
    @Operation(summary = "获取待办事项列表")
    public Result<List<TodoItemDTO>> getTodoList(
            @RequestHeader("Authorization") String token) throws Exception {
        Long studentId = JwtUtil.getUserIdFromToken(token);
        List<TodoItemDTO> todoList = dashboardService.getTodoList(studentId);
        return Result.success(todoList);
    }

    // 获取通知消息列表
    @GetMapping("/notifications")
    @Operation(summary = "获取通知消息列表")
    public Result<List<NotificationDTO>> getNotifications(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "5") Integer limit) throws Exception {
        Long studentId = JwtUtil.getUserIdFromToken(token);
        List<NotificationDTO> notifications = dashboardService.getNotifications(studentId, limit);
        return Result.success(notifications);
    }

    // 获取论文处理进度
    @GetMapping("/progress-tracking")
    @Operation(summary = "获取论文处理进度")
    public Result<ProgressTrackingDTO> getProgressTracking(
            @RequestHeader("Authorization") String token) throws Exception {
        Long studentId = JwtUtil.getUserIdFromToken(token);
        ProgressTrackingDTO progress = dashboardService.getProgressTracking(studentId);
        return Result.success(progress);
    }

    // 导出工作台数据（统计概览 + 论文明细，xlsx）
    @GetMapping("/export")
    @Operation(summary = "导出工作台数据", description = "导出统计概览与论文明细（xlsx）")
    public void exportDashboardData(
            @RequestHeader("Authorization") String token,
            HttpServletResponse response) {
        try {
            Long studentId = JwtUtil.getUserIdFromToken(token);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("工作台数据_" + LocalDate.now(), StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=utf-8''" + fileName + ".xlsx");
            dashboardService.exportDashboardData(studentId, response.getOutputStream());
        } catch (Exception e) {
            log.error("导出工作台数据失败", e);
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            try {
                response.getWriter().write("{\"code\":500,\"message\":\"导出工作台数据失败\"}");
            } catch (IOException ignored) {
            }
        }
    }
}
