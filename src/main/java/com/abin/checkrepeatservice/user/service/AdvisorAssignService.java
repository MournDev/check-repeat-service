package com.abin.checkrepeatservice.user.service;


import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 指导老师分配服务接口（核心业务：指导老师自动/手动分配、任务数校验与同步）
 */
public interface AdvisorAssignService {

    /**
     * 1. 自动分配指导老师（核心方法）
     * 逻辑：根据学生专业查询“有指导权限且未达上限”的教师，按当前任务数升序分配（负载均衡）
     *
     * @param paperSubmitId 论文ID（关联 paper_submit 表，需为“待分配”状态）
     * @return 分配结果（含分配的指导老师ID、论文状态更新结果）
     */
    Result<Boolean> autoAssignAdvisor(Long paperSubmitId);

    /**
     * 2. 手动分配指导老师（管理员操作）
     * 逻辑：支持管理员指定指导老师，需额外校验“指定教师是否有该专业指导权限+未达上限”
     * @param paperId 论文ID
     * @param teacherId 目标指导老师ID（管理员指定）
     * @param reason 原因
     * @return 分配结果
     */
    Result<?> manualAssignAdvisor(Long paperId, Long teacherId, String reason);

//    /**
//     * 3. 同步指导任务数（状态变更触发）
//     * 逻辑：当论文状态变更为“指导完成”“审核驳回”时，减少对应教师的当前指导任务数
//     * @param paperId 论文ID
//     * @param newStatus 论文新状态（如 3=指导完成，6=审核驳回）
//     */
//    void syncAdvisorTaskCount(Long paperId, Integer newStatus);
//
//    /**
//     * 4. 校验指导老师是否可接收新任务（供手动分配/接口调用）
//     * 逻辑：校验“教师是否有该专业指导权限”且“当前任务数 < 上限”
//     * @param advisorId 指导老师ID
//     * @param studentMajorId 学生所属专业ID（论文对应的专业）
//     * @return 可接收返回 true，否则返回 false 并提示原因
//     */
//    boolean checkAdvisorAvailable(Long advisorId, Long studentMajorId);
//
//    /**
//     * 5. 查询某教师的当前指导任务列表（供前端展示）
//     * @param advisorId 指导老师ID
//     * @param status 任务状态（可选：1=待指导，2=指导中，3=指导完成；null 查所有状态）
//     * @return 指导任务列表（关联 paper_submit 表数据）
//     */
//    List<PaperAdvisorTaskVO> getAdvisorTaskList(Long advisorId, Integer status);
//
//    /**
//     * 指导任务取消（学生/管理员触发，按角色控制权限）
//     * @param paperId 论文ID
//     * @param operatorId 操作人ID（用于判断角色：学生/管理员）
//     * @return 取消结果
//     */
//    Result<?> cancelAdvisorTask(Long paperId, Long operatorId);
//
//    /**
//     * 指导老师更换（管理员专属）
//     * @param paperId 论文ID
//     * @param newAdvisorId 新指导老师ID
//     * @param operatorId 操作人ID（需为管理员）
//     * @return 更换结果
//     */
//    Result<?> changeAdvisor(Long paperId, Long newAdvisorId, Long operatorId);
//
//    /**
//     * 查询某论文的所有轮次关联记录（用于前端展示多轮指导历史）
//     * @param paperId 论文ID
//     * @return 多轮关联记录列表（按轮次升序）
//     */
//    List<PaperAdvisorRel> listAllRoundsByPaperId(Long paperId);
}
