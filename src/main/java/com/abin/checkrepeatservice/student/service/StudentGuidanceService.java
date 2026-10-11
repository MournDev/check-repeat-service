package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.dto.GuidanceConfirmReq;
import com.abin.checkrepeatservice.student.vo.GuidanceRecordVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 学生端指导记录服务（论文全生命周期 P1）
 */
public interface StudentGuidanceService {

    /**
     * 查询我的指导记录（分页，可按确认状态过滤）
     *
     * @param confirmStatus 确认状态（可选：0-待确认，1-已确认，2-有异议）
     * @param currentPage   页码（从1开始）
     * @param pageSize      每页条数
     */
    Result<Page<GuidanceRecordVO>> listMyGuidance(Integer confirmStatus, Integer currentPage, Integer pageSize);

    /**
     * 学生确认指导记录（仅本人、仅待确认状态可确认，确认后固化）
     *
     * @param recordId 指导记录ID
     * @param req      学生反馈（可选）
     */
    Result<Void> confirmRecord(Long recordId, GuidanceConfirmReq req);
}
