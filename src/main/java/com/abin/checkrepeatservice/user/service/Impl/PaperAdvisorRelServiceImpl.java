package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Exception.ParamInvalidException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.PaperAdvisorRel;
import com.abin.checkrepeatservice.user.mapper.PaperAdvisorRelMapper;
import com.abin.checkrepeatservice.user.service.PaperAdvisorRelService;
import com.abin.checkrepeatservice.user.vo.PaperAdvisorRoundVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 论文-指导老师关联服务实现（适配 AdvisorAssignService 调用）
 */
@Service
public class PaperAdvisorRelServiceImpl extends ServiceImpl<PaperAdvisorRelMapper, PaperAdvisorRel> implements PaperAdvisorRelService {

    @Resource
    private PaperAdvisorRelMapper paperAdvisorRelMapper;

    // 常量：关联状态
    private static final Integer REL_STATUS_VALID = 1; // 有效
    private static final Integer REL_STATUS_INVALID = 0; // 无效
    // 常量：默认指导轮次
    static final Integer DEFAULT_ADVISOR_ROUND = 1;

    /**
     * 新增关联记录（分配指导老师时调用，避免重复分配）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveRel(PaperAdvisorRel rel) {
        // 步骤1：补全默认值（若前端未传）
        if (rel.getRelStatus() == null) {
            rel.setRelStatus(REL_STATUS_VALID); // 默认有效
        }
        if (rel.getAdvisorRound() == null) {
            rel.setAdvisorRound(DEFAULT_ADVISOR_ROUND); // 默认第一轮
        }
        // 补全创建人（若未传，如系统自动分配时设为0）
        if (rel.getCreateBy() == null) {
            rel.setCreateBy(0L);
        }

        // 步骤2：校验重复分配（同一论文同一轮次不允许存在有效记录）
        PaperAdvisorRel existingRel = getValidRelByPaperId(rel.getPaperId(), rel.getAdvisorRound());
        if (existingRel != null) {
            throw new BusinessException(ResultCode.BUSINESS_TASK_ASSIGNED ,"论文ID：" + rel.getPaperId() + " 第" + rel.getAdvisorRound() + "轮已分配指导老师，无需重复分配");
        }

        // 步骤3：新增记录（调用 MyBatis-Plus 的 save 方法）
        return this.save(rel);
    }
    /**
     * 标记关联记录为无效（任务完成/驳回时调用，保留历史记录）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean invalidateRel(Long paperId, Integer advisorRound) {
        // 步骤1：补全默认轮次
        if (advisorRound == null) {
            advisorRound = DEFAULT_ADVISOR_ROUND;
        }

        // 步骤2：查询当前有效记录
        PaperAdvisorRel validRel = getValidRelByPaperId(paperId, advisorRound);
        if (validRel == null) {
            throw new BusinessException(ResultCode.BUSINESS_NO_TASK,"论文ID：" + paperId + " 第" + advisorRound + "轮无有效指导记录，无需标记无效");
        }

        // 步骤3：更新状态为无效
        validRel.setRelStatus(REL_STATUS_INVALID);
        validRel.setUpdateBy(0L); // 0=系统自动更新（也可传操作人ID）
        return this.updateById(validRel);
    }
    /**
     * 查询某论文的当前有效关联记录（供 AdvisorAssignService 判断是否已分配）
     */
    @Override
    public PaperAdvisorRel getValidRelByPaperId(Long paperId, Integer advisorRound) {
        // 步骤1：参数校验
        if (paperId == null) {
            throw new ParamInvalidException(ResultCode.PARAM_EMPTY,null,"论文ID不能为空");
        }
        if (advisorRound == null) {
            advisorRound = DEFAULT_ADVISOR_ROUND;
        }

        // 步骤2：构建查询条件（论文ID+轮次+有效状态+未删除）
        QueryWrapper<PaperAdvisorRel> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("paper_id", paperId)
                .eq("advisor_round", advisorRound)
                .eq("rel_status", REL_STATUS_VALID)
                .eq("is_deleted", 0); // 过滤已删除的关联记录

        // 步骤3：查询唯一有效记录（同一论文同一轮次仅一条有效记录）
        return this.getOne(queryWrapper);
    }
    /**
     * 统计某指导老师的当前有效任务数（替代 sys_user_major 的 current_advisor_count，更精准）
     */
    @Override
    public int countValidRelByAdvisorId(Long advisorId, Long studentMajorId) {
        // 步骤1：参数校验
        if (advisorId == null) {
            throw new ParamInvalidException(ResultCode.PARAM_EMPTY,null,"指导老师ID不能为空");
        }

        // 步骤2：构建查询条件（指导老师ID+有效状态+未删除）
        QueryWrapper<PaperAdvisorRel> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("advisor_id", advisorId)
                .eq("rel_status", REL_STATUS_VALID)
                .eq("is_deleted", 0);

        // 步骤3：可选筛选（按学生专业，支持跨专业指导时的精准统计）
        if (studentMajorId != null) {
            queryWrapper.eq("student_major_id", studentMajorId);
        }

        // 步骤4：统计记录数
        return Math.toIntExact(this.count(queryWrapper));
    }
    /**
     * 查询某指导老师的所有关联记录（含历史任务，供前端展示）
     */
    @Override
    public List<PaperAdvisorRel> listRelByAdvisorId(Long advisorId, Integer relStatus) {
        // 步骤1：参数校验
        if (advisorId == null) {
            throw new ParamInvalidException(ResultCode.PARAM_EMPTY,null,"指导老师ID不能为空");
        }

        // 步骤2：构建查询条件（指导老师ID+未删除）
        QueryWrapper<PaperAdvisorRel> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("advisor_id", advisorId)
                .eq("is_deleted", 0)
                .orderByDesc("create_time"); // 按分配时间倒序（最新任务在前）

        // 步骤3：可选筛选（按关联状态：有效/无效/所有）
        if (relStatus != null) {
            queryWrapper.eq("rel_status", relStatus);
        }

        // 步骤4：查询列表（若需关联论文信息，需在 Mapper 中自定义关联查询）
        return this.list(queryWrapper);
    }
    @Override
    public List<PaperAdvisorRoundVO> listRoundHistoryWithDetail(Long paperId) {
        if (paperId == null) {
            throw new ParamInvalidException(ResultCode.PARAM_EMPTY,null,"论文ID不能为空");
        }
        // 调用 Mapper 自定义 SQL，关联 paper_submit + sys_user + major 表
        List<PaperAdvisorRoundVO> historyVOList = paperAdvisorRelMapper.selectRoundHistoryWithDetail(paperId);

        // 补充状态描述（如 1=有效，0=无效）
        if (!CollectionUtils.isEmpty(historyVOList)) {
            historyVOList.forEach(vo -> {
                PaperAdvisorRel rel = new PaperAdvisorRel(); // 仅用枚举映射，无需实例化完整对象
                vo.setRelStatusDesc(rel.getRelStatus() == 1 ? "有效" : "无效");
            });
        }
        return historyVOList;
    }

    @Override
    public List<PaperAdvisorRel> listAllRoundsByPaperId(Long paperId) {
        if (paperId == null) {
            throw new ParamInvalidException(ResultCode.PARAM_EMPTY,null ,"论文ID不能为空");
        }
        return this.list(new QueryWrapper<PaperAdvisorRel>()
                .eq("paper_id", paperId)
                .eq("is_deleted", 0)
                .orderByAsc("advisor_round"));
    }
}