package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface TeacherAssignmentService {
    /**
     * 教师确认接收论文
     */
    Result<Boolean> confirmAssignment(Long paperId, Long teacherId);

    /**
     * 教师手动拒绝接收论文
     */
    Result<Boolean> rejectAssignment(Long paperId, Long teacherId);

    /**
     * 分配超时未确认，由定时任务自动拒绝
     *
     * 与 {@link #rejectAssignment} 的差别只在发给学生的通知：手动拒绝发 TEACHER_REJECT_NOTICE
     * （「指导老师已拒绝接收」），超时拒绝发 TIMEOUT_REJECT_NOTICE
     * （「因指导老师超时未确认，已于 xx 自动拒绝」）——超时场景老师根本没操作过，
     * 发「已拒绝接收」会让学生以为是老师主动拒的。
     */
    Result<Boolean> rejectAssignmentByTimeout(Long paperId, Long teacherId);

    /**
     * 获取教师待确认的论文列表
     */
    Result<Page<PaperInfo>> getPendingConfirmPapers(Long teacherId, Integer pageNum, Integer pageSize);
}
