package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.student.dto.CheckTaskResultDTO;
import com.abin.checkrepeatservice.user.vo.CheckResultVO;

import java.util.List;

/**
 * 增强版查重任务服务接口
 * 提供更完善的任务管理和状态控制
 */
public interface EnhancedCheckTaskService {
    
    /**
     * 创建查重任务（增强版）
     * @param paperId 论文ID
     * @param engineTypes 引擎类型列表
     * @return 查重任务结果
     */
    Result<CheckResultVO> createEnhancedCheckTask(Long paperId, List<String> engineTypes);
    
    /**
     * 获取查重任务列表（支持分页和筛选）
     * @param paperId 论文ID（可选）
     * @param status 状态（可选）
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 任务列表
     */
    Result<List<CheckTaskResultDTO>> getCheckTaskList(Long paperId, String status, Integer pageNum, Integer pageSize);
    
    /**
     * 获取查重任务详情（包含完整状态信息）
     * @param taskId 任务ID
     * @return 任务详情
     */
    Result<CheckTaskResultDTO> getCheckTaskDetail(Long taskId);
    
    /**
     * 取消查重任务（带状态验证）
     * @param taskId 任务ID
     * @return 操作结果
     */
    Result<String> cancelCheckTask(Long taskId);
    
    /**
     * 重试失败的查重任务
     * @param taskId 任务ID
     * @return 操作结果
     */
    Result<String> retryCheckTask(Long taskId);
    
    /**
     * 获取任务状态统计
     * @return 状态统计信息
     */
    Result<Object> getTaskStatistics();
    
    /**
     * 更新任务状态（内部使用）
     * @param taskId 任务ID
     * @param newStatus 新状态
     * @param reason 变更原因
     * @return 是否成功
     */
    boolean updateTaskStatus(Long taskId, CheckTaskStatusEnum newStatus, String reason);
    
    /**
     * 获取任务执行进度
     * @param taskId 任务ID
     * @return 进度信息
     */
    Result<Object> getTaskProgress(Long taskId);
}