package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.ReviewSubmitReq;
import com.abin.checkrepeatservice.teacher.vo.ReviewAssignVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 教师评阅工作台服务（论文全生命周期 P2）。
 *
 * 线上/线下统一模型：线上系统内直接评；线下由评阅人本人提交结果回填 + 扫描件佐证，
 * 状态推进与线上一致，不做流程分支。
 */
public interface TeacherReviewAssignService {

    /**
     * 查询我的评阅任务（分页）
     *
     * @param status      状态过滤（可空：0-待评阅 / 2-已提交 / 3-已超时转派）
     * @param currentPage 页码（从1开始）
     * @param pageSize    每页条数
     */
    Result<Page<ReviewAssignVO>> listMyAssignments(Integer status, Integer currentPage, Integer pageSize);

    /**
     * 提交评阅结果（仅本人任务；仅待评阅/评阅中可提交）
     *
     * @param assignmentId 评阅任务ID
     * @param req          评分/意见/扫描件
     */
    Result<ReviewAssignVO> submit(Long assignmentId, ReviewSubmitReq req);
}
