package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.vo.ThesisTimelineVO;

/**
 * 学生端论文进度时间线服务（论文全生命周期 P1）
 */
public interface StudentThesisProgressService {

    /**
     * 查询论文进度时间线（当前阶段 + 状态流转历史）
     *
     * @param paperId 论文ID（可选；不传时取当前学生最近提交的一篇论文）
     */
    Result<ThesisTimelineVO> getTimeline(Long paperId);
}
