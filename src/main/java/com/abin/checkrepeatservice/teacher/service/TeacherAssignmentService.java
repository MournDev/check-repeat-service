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
     * 教师拒绝接收论文
     */
    Result<Boolean> rejectAssignment(Long paperId, Long teacherId);

    /**
     * 获取教师待确认的论文列表
     */
    Result<Page<PaperInfo>> getPendingConfirmPapers(Long teacherId, Integer pageNum, Integer pageSize);
}
