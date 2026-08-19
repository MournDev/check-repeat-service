package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.admin.dto.AssignmentRuleConfigDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * 管理员论文分配服务接口
 */
public interface AdminAssignmentService {
    
    /**
     * 获取分配统计信息
     */
    Result<Map<String, Object>> getAssignmentStats();
    
    /**
     * 获取未分配学生列表
     */
    Result<Page<Map<String, Object>>> getUnassignedStudents(String keyword, Integer page, Integer size);
    
    /**
     * 获取可用教师列表
     */
    Result<Page<Map<String, Object>>> getAvailableTeachers(String keyword, Integer page, Integer size);
    
    /**
     * 单个学生分配指导老师
     */
    Result<String> singleAssign(String studentId, String teacherId, String remark);
    
    /**
     * 批量学生分配指导老师
     */
    Result<Map<String, Object>> batchAssign(List<String> studentIds, String teacherId, String strategy, String remark);
    
    /**
     * 获取专业名称映射
     */
    Result<Map<String, String>> getMajorNameMap();
    
    /**
     * 获取分配规则配置
     */
    Result<AssignmentRuleConfigDTO> getAssignmentRules();
    
    /**
     * 保存分配规则配置
     */
    Result<String> saveAssignmentRules(AssignmentRuleConfigDTO config);
    
    /**
     * 刷新分配相关数据
     */
    Result<String> refreshAssignmentData();
}