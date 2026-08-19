package com.abin.checkrepeatservice.common.service;

import com.abin.checkrepeatservice.common.Result;

import java.util.List;
import java.util.Map;

/**
 * 公共字典服务接口
 * 提供无权限限制的字典数据查询，供所有角色使用
 */
public interface CommonDictService {

    /**
     * 获取学院列表
     * @return 学院列表（value/label格式）
     */
    Result<List<Map<String, Object>>> getColleges();

    /**
     * 获取专业列表
     * @param collegeId 学院ID（可选，不传则返回所有专业）
     * @return 专业列表（value/label格式）
     */
    Result<List<Map<String, Object>>> getMajors(Long collegeId);

    /**
     * 获取年级列表
     * @return 年级列表（value/label格式）
     */
    Result<List<Map<String, Object>>> getGrades();

    /**
     * 获取专业名称映射
     * @return 专业ID到专业名称的映射
     */
    Result<Map<String, String>> getMajorNameMap();
}
