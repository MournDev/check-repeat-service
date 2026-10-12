package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.dto.AdvisorEvalStatusVO;
import com.abin.checkrepeatservice.student.dto.AdvisorEvalSubmitReq;

/**
 * 学生端导师评价服务（论文全生命周期 P6a）。
 *
 * 核心规则：
 *  - 时机：论文状态 defense_passed/finalized 后开放（软提醒，不卡定稿/归档阶段门）；
 *  - 窗口：defense_passed 日志时间 + admin.advisor-eval.window-days（默认30天），
 *    超窗抛 40919；存量无状态日志的论文不做窗口限制；
 *  - 每篇论文一次：uk_paper_id 兜底，重复提交抛 40918，提交后不可改。
 */
public interface AdvisorEvaluationService {

    /**
     * 提交导师评价（仅论文学生本人，导师评价分不进学生成绩合成）
     *
     * @return 评价ID
     */
    Result<String> submit(AdvisorEvalSubmitReq req);

    /**
     * 我的导师评价状态：评价入口所需的全部状态（可评性/窗口/已提交回显/可选标签）
     *
     * @param paperId 论文ID，可空（空则取学生最新一篇论文）
     */
    Result<AdvisorEvalStatusVO> my(Long paperId);
}
