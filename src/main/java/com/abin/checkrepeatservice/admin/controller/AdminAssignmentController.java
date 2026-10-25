package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminAssignmentService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.admin.dto.AssignmentRuleConfigDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 管理员论文分配控制器
 * 负责处理论文指导老师分配相关的所有接口
 */
@RestController
@RequestMapping("/api/admin/assignment")
@Slf4j
@Tag(name = "论文分配管理", description = "分配统计/未分配学生/指导老师分配与规则配置")
public class AdminAssignmentController {

    @Resource
    private AdminAssignmentService adminAssignmentService;

    /**
     * 1. 获取分配统计信息
     * 显示未分配学生数、可用教师数、平均指导数、今日分配数
     */
    @Operation(summary = "获取分配统计信息", description = "未分配学生数、可用教师数、平均指导数、今日分配数")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getAssignmentStats() {
        log.info("接收获取分配统计信息请求");
        return adminAssignmentService.getAssignmentStats();
    }

    /**
     * 2. 获取未分配学生列表
     * 获取所有尚未分配指导老师的待分配学生
     */
    @Operation(summary = "获取未分配学生列表", description = "分页查询尚未分配指导老师的待分配学生")
    @GetMapping("/unassigned-students")
    public Result<Page<Map<String, Object>>> getUnassignedStudents(
            @Parameter(description = "搜索关键字（可选）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("接收获取未分配学生列表请求: keyword={}, page={}, size={}", keyword, page, size);
        return adminAssignmentService.getUnassignedStudents(keyword, page, size);
    }

    /**
     * 3. 获取可用教师列表
     * 获取所有可接受学生的指导教师
     */
    @Operation(summary = "获取可用教师列表", description = "分页查询可接受学生的指导教师")
    @GetMapping("/available-teachers")
    public Result<Page<Map<String, Object>>> getAvailableTeachers(
            @Parameter(description = "搜索关键字（可选）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("接收获取可用教师列表请求: keyword={}, page={}, size={}", keyword, page, size);
        return adminAssignmentService.getAvailableTeachers(keyword, page, size);
    }

    /**
     * 4. 单个学生分配指导老师
     * 为单个学生分配指定的指导老师
     */
    @Operation(summary = "单个学生分配指导老师", description = "为单个学生分配指定的指导老师")
    @PostMapping("/single-assign")
    public Result<String> singleAssign(
            @Parameter(description = "分配请求体：studentId 学生ID、teacherId 教师ID、remark 备注", required = true)
            @RequestBody Map<String, Object> request) {
        log.info("接收单个分配请求: {}", request);
        String studentId = (String) request.get("studentId");
        String teacherId = (String) request.get("teacherId");
        String remark = (String) request.get("remark");
        
        if (studentId == null || teacherId == null) {
            return Result.error(ResultCode.PARAM_ERROR, "学生ID和教师ID不能为空");
        }
        
        return adminAssignmentService.singleAssign(studentId, teacherId, remark);
    }

    /**
     * 5. 批量学生分配指导老师
     * 为多个学生同时分配同一个指导老师
     */
    @Operation(summary = "批量学生分配指导老师", description = "为多个学生同时分配同一个指导老师")
    @PostMapping("/batch-assign")
    public Result<Map<String, Object>> batchAssign(
            @Parameter(description = "批量分配请求体：studentIds 学生ID列表、teacherId 教师ID、strategy 分配策略、remark 备注", required = true)
            @RequestBody Map<String, Object> request) {
        log.info("接收批量分配请求: {}", request);
        @SuppressWarnings("unchecked")
        List<String> studentIds = (List<String>) request.get("studentIds");
        String teacherId = (String) request.get("teacherId");
        String strategy = (String) request.get("strategy");
        String remark = (String) request.get("remark");
        
        if (studentIds == null || studentIds.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "学生ID列表不能为空");
        }
        if (teacherId == null) {
            return Result.error(ResultCode.PARAM_ERROR, "教师ID不能为空");
        }
        
        return adminAssignmentService.batchAssign(studentIds, teacherId, strategy, remark);
    }

    /**
     * 6. 获取专业名称映射
     * 将专业代码转换为专业名称显示
     */
    @Operation(summary = "获取专业名称映射", description = "将专业代码转换为专业名称显示")
    @GetMapping("/majors/map")
    public Result<Map<String, String>> getMajorNameMap() {
        log.info("接收获取专业名称映射请求");
        return adminAssignmentService.getMajorNameMap();
    }

    /**
     * 7. 获取分配规则配置
     * 管理自动分配的规则设置
     */
    @Operation(summary = "获取分配规则配置", description = "读取自动分配的规则设置")
    @GetMapping("/rules")
    public Result<AssignmentRuleConfigDTO> getAssignmentRules() {
        log.info("接收获取分配规则配置请求");
        return adminAssignmentService.getAssignmentRules();
    }

    /**
     * 7. 保存分配规则配置
     */
    @Operation(summary = "保存分配规则配置", description = "保存自动分配的规则设置")
    @PostMapping("/rules")
    public Result<String> saveAssignmentRules(
            @Parameter(description = "分配规则配置：自动分配规则设置", required = true)
            @RequestBody AssignmentRuleConfigDTO config) {
        log.info("接收保存分配规则配置请求: {}", config);
        return adminAssignmentService.saveAssignmentRules(config);
    }

    /**
     * 8. 刷新分配相关数据
     * 手动刷新页面上的统计数据和列表
     */
    @Operation(summary = "刷新分配数据", description = "手动刷新页面上的统计数据和列表")
    @PostMapping("/refresh")
    public Result<String> refreshAssignmentData() {
        log.info("接收刷新分配数据请求");
        return adminAssignmentService.refreshAssignmentData();
    }
}