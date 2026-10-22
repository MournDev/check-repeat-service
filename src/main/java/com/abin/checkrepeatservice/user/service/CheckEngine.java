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
     * 检查论文重复（带排除论文ID）。
     *
     * <p>架构修复（P0）：待查论文在比对前已先入比对库，若不排除自己，自我相似度
     * 必然 100%。默认实现忽略 excludePaperId，仅本地库引擎需要实现排除语义。</p>
     *
     * @param paperContent   论文内容
     * @param paperTitle     论文标题
     * @param excludePaperId 必须排除出比对库的论文ID（通常就是待查论文自身），null 不排除
     * @return 检查结果
     */
    default CheckResult check(String paperContent, String paperTitle, Long excludePaperId) {
        return check(paperContent, paperTitle);
    }
    
    /**
     * 引擎是否已真实实现（M1.1 检测边界明示）。
     *
     * <p>预留引擎（第三方 API、深度学习）只保留接入路径，未对接真实数据源。
     * 调用方必须在建任务前用本方法判定，未实现时明确报错，禁止把“未接入”
     * 静默降级成 0% 结果。</p>
     *
     * @return true-可参与真实查重；false-仅预留、不可用
     */
    default boolean isImplemented() {
        return true;
    }

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
