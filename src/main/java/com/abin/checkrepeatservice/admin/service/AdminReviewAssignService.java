package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.ReviewAllocateReq;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.vo.ReviewAssignVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 管理端评阅任务分配服务（论文全生命周期 P2）。
 *
 * 评阅任务不接入 review_workflow 配置（评阅有独立的任务分配模型）；
 * 分配入口在初审通过（completed）之后，首次分配时论文状态经阶段门推进为 reviewing。
 */
public interface AdminReviewAssignService {

    /**
     * 分配评阅任务（手动指定评阅人 / 自动挑选评阅人）
     *
     * @param req 分配请求
     * @return total-请求数 / success-成功数 / skipped-跳过数 / skipReasons-跳过明细
     */
    Result<Map<String, Object>> allocate(ReviewAllocateReq req);

    /**
     * 评阅任务列表（分页，管理端监控与改派入口）
     *
     * @param paperId     论文ID过滤（可空）
     * @param reviewerId  评阅人ID过滤（可空）
     * @param status      状态过滤（可空）
     */
    Result<Page<ReviewAssignVO>> listAssignments(Long paperId, Long reviewerId, Integer status,
                                                 Integer currentPage, Integer pageSize);
}
