package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.AdvisorEvalSummaryVO;

/**
 * 教师端导师评价汇总服务（论文全生命周期 P6a）。
 *
 * 匿名保护是核心约束：教师端只暴露聚合结果，永不返回单条评价明细与文字评语；
 * 评价份数 < 3 时掩码（小样本可被反推身份，行业通行阈值）。
 */
public interface TeacherAdvisorEvalService {

    /**
     * 我收到的导师评价汇总（五维均分 + 导师评价分 + 标签频次）
     */
    Result<AdvisorEvalSummaryVO> summary();
}
