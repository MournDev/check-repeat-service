package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.DefenseBatchReq;
import com.abin.checkrepeatservice.admin.dto.DefenseEnrollReq;
import com.abin.checkrepeatservice.admin.dto.DefenseGroupMemberReq;
import com.abin.checkrepeatservice.admin.vo.DefenseBatchVO;
import com.abin.checkrepeatservice.admin.vo.DefenseGroupMemberVO;
import com.abin.checkrepeatservice.admin.vo.DefenseRecordExcelVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.vo.DefenseRecordVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

/**
 * 管理端答辩批次管理服务接口（论文全生命周期 P3）。
 *
 * 职责：
 *  - 答辩批次 CRUD（中期/终期共用，batch_type 区分）；
 *  - 答辩组分配（1 组长 + N 成员）；
 *  - 学生入批（按批次类型经阶段门推进 paper_status）；
 *  - 批次内记录列表（监控与导出入口）。
 */
public interface AdminDefenseService {

    /**
     * 新建答辩批次
     */
    Result<DefenseBatchVO> createBatch(@Valid DefenseBatchReq req);

    /**
     * 编辑答辩批次（批次编号与类型不可改）
     */
    Result<DefenseBatchVO> updateBatch(Long id, @Valid DefenseBatchReq req);

    /**
     * 答辩批次分页列表
     *
     * @param batchType mid_term / final（可空）
     * @param collegeId 学院ID（可空）
     * @param status    批次状态 0/1/2（可空）
     */
    Result<Page<DefenseBatchVO>> listBatches(String batchType, Long collegeId, Integer status,
                                            Integer currentPage, Integer pageSize);

    /**
     * 答辩批次详情（含组成员数、入册论文数、已提交结论数）
     */
    Result<DefenseBatchVO> getBatchDetail(Long id);

    /**
     * 删除答辩批次（仅筹备中状态可删，软删）
     */
    Result<Void> deleteBatch(Long id);

    /**
     * 分配答辩组成员（1 组长 + N 成员）
     */
    Result<Map<String, Object>> assignMembers(Long batchId, @Valid DefenseGroupMemberReq req);

    /**
     * 批次答辩组成员名单（组长在前，成员在后，同角色按加入时间升序）
     */
    Result<List<DefenseGroupMemberVO>> listBatchMembers(Long batchId);

    /**
     * 学生入批（按批次类型经阶段门推进 paper_status）
     */
    Result<Map<String, Object>> enrollStudents(Long batchId, @Valid DefenseEnrollReq req);

    /**
     * 批次内答辩记录列表（监控与导出入口）
     */
    Result<Page<DefenseRecordVO>> listBatchRecords(Long batchId, Long studentId, String result,
                                                   Integer currentPage, Integer pageSize);

    /**
     * 构建批次内答辩记录导出数据（EasyExcel 行数据，不分页，按顺序号升序）
     */
    List<DefenseRecordExcelVO> buildExportRecords(Long batchId);
}
