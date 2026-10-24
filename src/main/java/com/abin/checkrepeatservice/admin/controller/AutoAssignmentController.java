package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AutoAssignmentService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.admin.dto.AutoAssignmentConfigDTO;
import com.abin.checkrepeatservice.admin.dto.AutoAssignmentPreviewDTO;
import com.abin.checkrepeatservice.admin.dto.AutoAssignmentStartDTO;
import com.abin.checkrepeatservice.admin.dto.AutoAssignmentProgressDTO;
import com.abin.checkrepeatservice.admin.dto.AutoAssignmentHistoryDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 自动论文分配控制器
 * 负责处理自动分配算法的配置、执行和监控
 */
@RestController
@RequestMapping("/api/admin/auto-assignment")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class AutoAssignmentController {

    @Resource
    private AutoAssignmentService autoAssignmentService;

    /**
     * 1.1 获取算法配置
     * 用途：加载页面时获取当前的算法配置参数
     */
    @GetMapping("/config")
    public Result<AutoAssignmentConfigDTO> getAlgorithmConfig() {
        log.info("接收获取算法配置请求");
        return autoAssignmentService.getAlgorithmConfig();
    }

    /**
     * 1.2 保存算法配置
     * 用途：保存用户修改的算法配置参数
     */
    @PostMapping("/config")
    public Result<String> saveAlgorithmConfig(@RequestBody AutoAssignmentConfigDTO config) {
        log.info("接收保存算法配置请求: {}", config);
        return autoAssignmentService.saveAlgorithmConfig(config);
    }

    /**
     * 2.1 获取分配预览数据
     * 用途：显示待分配学生数、可用教师数等预览信息
     */
    @GetMapping("/preview")
    public Result<AutoAssignmentPreviewDTO> getAssignmentPreview() {
        log.info("接收获取分配预览数据请求");
        return autoAssignmentService.getAssignmentPreview();
    }

    /**
     * 3.1 启动自动分配
     * 用途：触发自动分配算法执行
     */
    @PostMapping("/start")
    public Result<Map<String, Object>> startAutoAssignment(@RequestBody AutoAssignmentStartDTO request) {
        log.info("接收启动自动分配请求: {}", request);
        return autoAssignmentService.startAutoAssignment(request);
    }

    /**
     * 3.2 查询分配进度
     * 用途：实时获取分配任务的执行进度
     */
    @GetMapping("/progress/{taskId}")
    public Result<AutoAssignmentProgressDTO> getAssignmentProgress(@PathVariable String taskId) {
        log.info("接收查询分配进度请求: taskId={}", taskId);
        return autoAssignmentService.getAssignmentProgress(taskId);
    }

    /**
     * 3.3 取消分配任务
     * 用途：用户主动取消分配任务
     */
    @PostMapping("/cancel/{taskId}")
    public Result<String> cancelAssignmentTask(@PathVariable String taskId) {
        log.info("接收取消分配任务请求: taskId={}", taskId);
        return autoAssignmentService.cancelAssignmentTask(taskId);
    }

    /**
     * 4.1 获取执行历史列表
     * 用途：显示历史分配任务记录
     */
    @GetMapping("/history")
    public Result<Page<AutoAssignmentHistoryDTO>> getAssignmentHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("接收获取执行历史列表请求: page={}, size={}", page, size);
        return autoAssignmentService.getAssignmentHistory(page, size);
    }

    /**
     * 4.2 获取执行详情
     * 用途：查看某次分配任务的详细分配结果
     */
    @GetMapping("/history/{id}/detail")
    public Result<Map<String, Object>> getAssignmentDetail(@PathVariable String id) {
        log.info("接收获取执行详情请求: id={}", id);
        return autoAssignmentService.getAssignmentDetail(id);
    }

    /**
     * 4.3 应用分配结果
     * 用途：将历史成功的分配结果应用到当前系统
     */
    @PostMapping("/history/{id}/apply")
    public Result<String> applyAssignmentResult(@PathVariable String id) {
        log.info("接收应用分配结果请求: id={}", id);
        return autoAssignmentService.applyAssignmentResult(id);
    }

    /**
     * 5.1 刷新基础数据
     * 用途：手动刷新页面上的统计数据
     */
    @PostMapping("/refresh")
    public Result<String> refreshBaseData() {
        log.info("接收刷新基础数据请求");
        return autoAssignmentService.refreshBaseData();
    }
}