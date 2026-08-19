package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.common.Result;

import java.util.List;
import java.util.Map;

/**
 * 管理员公共字典服务接口
 */
public interface AdminDictService {
    
    /**
     * 获取专业列表
     */
    Result<List<Map<String, Object>>> getMajors();
    
    /**
     * 获取年级列表
     */
    Result<List<Map<String, Object>>> getGrades();
    
    /**
     * 获取学院列表
     */
    Result<List<Map<String, Object>>> getColleges();
    
    /**
     * 获取专业名称映射
     */
    Result<Map<String, String>> getMajorNameMap();
}