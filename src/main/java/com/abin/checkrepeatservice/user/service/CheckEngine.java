package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum;
import com.abin.checkrepeatservice.pojo.vo.CheckResult;

/**
 * 查重引擎接口
 * 定义统一的查重引擎标准
 */
public interface CheckEngine {
    
    /**
     * 获取引擎类型
     * @return 引擎类型枚举
     */
    CheckEngineTypeEnum getEngineType();
    
    /**
     * 检查论文重复
     * @param paperContent 论文内容
     * @param paperTitle 论文标题
     * @return 检查结果
     */
    CheckResult check(String paperContent, String paperTitle);
    
    /**
     * 检查引擎健康状态
     * @return 是否健康可用
     */
    default boolean isHealthy() {
        return true;
    }
    
    /**
     * 获取引擎配置信息
     * @return 配置信息字符串
     */
    default String getConfigInfo() {
        return "{}";
    }
}
