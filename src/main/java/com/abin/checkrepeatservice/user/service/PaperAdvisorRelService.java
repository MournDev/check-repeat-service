package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.pojo.entity.PaperAdvisorRel;
import com.abin.checkrepeatservice.user.vo.PaperAdvisorRoundVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 论文-指导老师关联服务接口（管理关联关系的增删改查与统计）
 */
public interface PaperAdvisorRelService extends IService<PaperAdvisorRel> {

    /**
     * 1. 新增关联记录（分配指导老师时调用）
     * 逻辑：封装默认值（如状态=有效、轮次=1），避免重复分配（同一论文同一轮次仅允许一条有效记录）
     * @param rel 关联记录实体
     * @return 新增成功返回 true，失败抛异常
     */
    boolean saveRel(PaperAdvisorRel rel);

    /**
     * 2. 标记关联记录为无效（任务完成/驳回/取消分配时调用）
     * 逻辑：更新 rel_status=0，而非物理删除，保留历史记录
     * @param paperId 论文ID
     * @param advisorRound 指导轮次（默认1，多轮指导需指定）
     * @return 更新成功返回 true
     */
    boolean invalidateRel(Long paperId, Integer advisorRound);

    /**
     * 3. 查询某论文的当前有效关联记录（获取当前指导老师）
     * @param paperId 论文ID
     * @param advisorRound 指导轮次（默认1）
     * @return 有效关联记录（rel_status=1），无则返回 null
     */
    PaperAdvisorRel getValidRelByPaperId(Long paperId, Integer advisorRound);

    /**
     * 4. 查询某指导老师的有效关联记录数（统计当前指导任务数）
     * 逻辑：替代 sys_user_major 表的 current_advisor_count，支持更精准的统计（按轮次、专业筛选）
     * @param advisorId 指导老师ID
     * @param studentMajorId 学生专业ID（可选，null 查所有专业）
     * @return 有效关联记录总数（当前任务数）
     */
    int countValidRelByAdvisorId(Long advisorId, Long studentMajorId);

    /**
     * 5. 查询某指导老师的关联记录列表（历史任务+当前任务）
     * @param advisorId 指导老师ID
     * @param relStatus 关联状态（可选：0=无效，1=有效，null=所有）
     * @return 关联记录列表（含论文信息，需关联 paper_submit 表）
     */
    List<PaperAdvisorRel> listRelByAdvisorId(Long advisorId, Integer relStatus);

    /**
     * 查询论文多轮指导历史（含论文/指导老师详情，供前端展示）
     * @param paperId 论文ID
     * @return 适配前端的多轮历史 VO 列表
     */
    List<PaperAdvisorRoundVO> listRoundHistoryWithDetail(Long paperId);

    /**
     * 6. 查询某论文的全部多轮指导记录（含指导老师详情，供前端展示）
     * @param paperId 论文ID
     * @return 适配前端的多轮历史 VO 列表
     */
    List<PaperAdvisorRel> listAllRoundsByPaperId(Long paperId);
}
