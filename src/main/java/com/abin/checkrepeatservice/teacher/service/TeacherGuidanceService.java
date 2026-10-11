package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.vo.GuidanceRecordVO;
import com.abin.checkrepeatservice.teacher.dto.GuidanceCreateReq;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 教师端指导记录服务（论文全生命周期 P1）
 */
public interface TeacherGuidanceService {

    /**
     * 发起指导记录（学生待确认），同论文轮次自增，(paper_id, round) 唯一
     *
     * @param req 指导记录内容
     * @return 新建的指导记录 VO
     */
    Result<GuidanceRecordVO> createGuidance(GuidanceCreateReq req);

    /**
     * 查询我发起的指导记录（分页，可按论文过滤）
     *
     * @param paperId     论文ID（可选）
     * @param currentPage 页码（从1开始）
     * @param pageSize    每页条数
     */
    Result<Page<GuidanceRecordVO>> listMyGuidance(Long paperId, Integer currentPage, Integer pageSize);
}
