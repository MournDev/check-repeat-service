package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.admin.vo.DefenseBatchVO;
import com.abin.checkrepeatservice.admin.vo.DefenseGroupMemberVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.DefenseScoreReq;
import com.abin.checkrepeatservice.teacher.vo.DefenseRecordVO;
import jakarta.validation.Valid;

/**
 * 教师端答辩工作台服务接口（论文全生命周期 P3）。
 *
 * 职责：
 *  - 我的答辩批次列表（教师作为组成员或秘书参与的批次）；
 *  - 答辩批次会议信息（时间/地点/方式/组长/秘书/组员名单）；
 *  - 批次内答辩记录列表（组长视角可看全部，组员视角可看本批次列表）；
 *  - 评分录入（仅组长可提交结论；终期 result=pass 时回写 final_score 并推进 defense_passed）。
 */
public interface TeacherDefenseService {

    /**
     * 我的答辩批次列表（我作为组员/组长/秘书参与的批次）
     */
    Result<java.util.List<DefenseBatchVO>> listMyBatches();

    /**
     * 答辩批次会议信息（须为本批次组成员或秘书）
     */
    Result<DefenseBatchVO> getBatchDetail(Long batchId);

    /**
     * 批次答辩组成员名单（须为本批次组成员或秘书；组长在前，成员在后）
     */
    Result<java.util.List<DefenseGroupMemberVO>> listBatchMembers(Long batchId);

    /**
     * 批次内答辩记录列表（须为本批次组成员或秘书）
     */
    Result<java.util.List<DefenseRecordVO>> listBatchRecords(Long batchId, String result);

    /**
     * 评分录入（仅组长可提交结论；终期 result=pass 时回写 final_score 并推进 defense_passed）
     */
    Result<DefenseRecordVO> submitScore(@Valid DefenseScoreReq req);
}
