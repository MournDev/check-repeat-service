package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.admin.dto.AutoAssignmentConfigDTO;
import com.abin.checkrepeatservice.admin.dto.AutoAssignmentPreviewDTO;
import com.abin.checkrepeatservice.admin.dto.AutoAssignmentStartDTO;
import com.abin.checkrepeatservice.admin.dto.AutoAssignmentProgressDTO;
import com.abin.checkrepeatservice.admin.dto.AutoAssignmentHistoryDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 自动论文分配服务接口
 */
public interface AutoAssignmentService {
    
    /**
     * 获取算法配置
     */
    Result<AutoAssignmentConfigDTO> getAlgorithmConfig();
    
    /**
     * 保存算法配置
     */
    Result<String> saveAlgorithmConfig(AutoAssignmentConfigDTO config);
    
    /**
     * 获取分配预览数据
     */
    Result<AutoAssignmentPreviewDTO> getAssignmentPreview();
    
    /**
     * 启动自动分配
     */
    Result<Map<String, Object>> startAutoAssignment(AutoAssignmentStartDTO request);
    
    /**
     * 查询分配进度
     */
    Result<AutoAssignmentProgressDTO> getAssignmentProgress(String taskId);
    
    /**
     * 取消分配任务
     */
    Result<String> cancelAssignmentTask(String taskId);
    
    /**
     * 获取执行历史列表
     */
    Result<Page<AutoAssignmentHistoryDTO>> getAssignmentHistory(Integer page, Integer size);
    
    /**
     * 获取执行详情
     */
    Result<Map<String, Object>> getAssignmentDetail(String id);
    
    /**
     * 应用分配结果
     */
    Result<String> applyAssignmentResult(String id);
    
    /**
     * 刷新基础数据
     */
    Result<String> refreshBaseData();
}