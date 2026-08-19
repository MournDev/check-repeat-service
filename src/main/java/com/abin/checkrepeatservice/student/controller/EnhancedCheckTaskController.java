package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.dto.CheckTaskResultDTO;
import com.abin.checkrepeatservice.student.service.EnhancedCheckTaskService;
import com.abin.checkrepeatservice.user.vo.CheckResultVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 增强版查重任务控制器
 * 提供更完善的API接口和功能
 */
@RestController
@RequestMapping("/api/enhanced/check-tasks")
public class EnhancedCheckTaskController {
    
    @Resource
    private EnhancedCheckTaskService enhancedCheckTaskService;
    
    /**
     * 创建增强版查重任务
     */
    @PostMapping("/create")
    public Result<CheckResultVO> createEnhancedCheckTask(
            @RequestParam Long paperId,
            @RequestParam(required = false) List<String> engineTypes) {
        return enhancedCheckTaskService.createEnhancedCheckTask(paperId, engineTypes);
    }
    
    /**
     * 获取查重任务列表（分页）
     */
    @GetMapping("/list")
    public Result<List<CheckTaskResultDTO>> getCheckTaskList(
            @RequestParam(required = false) Long paperId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return enhancedCheckTaskService.getCheckTaskList(paperId, status, pageNum, pageSize);
    }
    
    /**
     * 获取查重任务详情
     */
    @GetMapping("/{taskId}")
    public Result<CheckTaskResultDTO> getCheckTaskDetail(@PathVariable Long taskId) {
        return enhancedCheckTaskService.getCheckTaskDetail(taskId);
    }
    
    /**
     * 取消查重任务
     */
    @PostMapping("/{taskId}/cancel")
    public Result<String> cancelCheckTask(@PathVariable Long taskId) {
        return enhancedCheckTaskService.cancelCheckTask(taskId);
    }
    
    /**
     * 重试失败的查重任务
     */
    @PostMapping("/{taskId}/retry")
    public Result<String> retryCheckTask(@PathVariable Long taskId) {
        return enhancedCheckTaskService.retryCheckTask(taskId);
    }
    
    /**
     * 获取任务状态统计
     */
    @GetMapping("/statistics")
    public Result<Object> getTaskStatistics() {
        return enhancedCheckTaskService.getTaskStatistics();
    }
    
    /**
     * 获取任务执行进度
     */
    @GetMapping("/{taskId}/progress")
    public Result<Object> getTaskProgress(@PathVariable Long taskId) {
        return enhancedCheckTaskService.getTaskProgress(taskId);
    }
}