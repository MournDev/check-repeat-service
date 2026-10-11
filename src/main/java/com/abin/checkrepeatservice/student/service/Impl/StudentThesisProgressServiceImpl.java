package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.component.PaperStageGate;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.constant.DictConstants.PaperStatus;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperStatusLog;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.StudentThesisProgressService;
import com.abin.checkrepeatservice.student.vo.ThesisTimelineVO;
import com.abin.checkrepeatservice.user.mapper.PaperStatusLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

/**
 * 学生端论文进度时间线服务实现（论文全生命周期 P1）。
 *
 * 数据说明：
 *  - 当前阶段以 paper_info.paper_status 为准；
 *  - 流转历史读 paper_status_log（paper_id 关联 paper_info.id）；
 *  - 历史行可能是早期代码写入的数字状态码（"2"/"3"/"4"），
 *    统一先过 PaperStageGate.normalizeLegacy 归一化再翻译标签。
 */
@Slf4j
@Service
public class StudentThesisProgressServiceImpl implements StudentThesisProgressService {

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private PaperStatusLogMapper paperStatusLogMapper;

    @Resource
    private PaperStageGate paperStageGate;

    @Override
    public Result<ThesisTimelineVO> getTimeline(Long paperId) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();

        // 1. 定位论文：显式传 paperId 时校验归属，否则取最近提交的一篇
        PaperInfo paper;
        if (paperId != null) {
            paper = paperInfoMapper.selectById(paperId);
            if (paper == null || Objects.equals(paper.getIsDeleted(), 1)) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
            }
            if (!studentId.equals(paper.getStudentId())) {
                throw new BusinessException(ResultCode.PERMISSION_STUDENT_OWNER, "仅学生本人可查看自己的论文进度");
            }
        } else {
            paper = paperInfoMapper.selectOne(new LambdaQueryWrapper<PaperInfo>()
                    .eq(PaperInfo::getStudentId, studentId)
                    .orderByDesc(PaperInfo::getSubmitTime)
                    .last("LIMIT 1"));
            if (paper == null) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "暂无论文，无法生成进度时间线");
            }
        }

        // 2. 流转历史（按时间正序，前端直接顺序渲染）
        List<PaperStatusLog> logs = paperStatusLogMapper.selectList(
                new LambdaQueryWrapper<PaperStatusLog>()
                        .eq(PaperStatusLog::getPaperId, paper.getId())
                        .orderByAsc(PaperStatusLog::getCreateTime));

        ThesisTimelineVO vo = new ThesisTimelineVO();
        vo.setPaperId(paper.getId());
        vo.setPaperTitle(paper.getPaperTitle());
        vo.setPaperStatus(paper.getPaperStatus());
        vo.setPaperStatusDesc(labelOf(paper.getPaperStatus()));
        vo.setNodes(logs.stream().map(this::toNode).toList());

        return Result.success("论文进度时间线查询成功", vo);
    }

    // ------------------------------ 私有辅助方法 ------------------------------

    private ThesisTimelineVO.Node toNode(PaperStatusLog statusLog) {
        ThesisTimelineVO.Node node = new ThesisTimelineVO.Node();
        String oldStatus = paperStageGate.normalizeLegacy(statusLog.getOldStatus());
        String newStatus = paperStageGate.normalizeLegacy(statusLog.getNewStatus());
        node.setOldStatus(oldStatus);
        node.setOldStatusDesc(labelOf(oldStatus));
        node.setNewStatus(newStatus);
        node.setNewStatusDesc(labelOf(newStatus));
        node.setReason(statusLog.getStatusReason());
        node.setOperateTime(statusLog.getCreateTime());
        return node;
    }

    /**
     * 状态值 → 中文标签。仅收录 P1 状态机的 13 个合法值；
     * 历史脏数据不认识的值原样返回，不猜标签。
     */
    private String labelOf(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return switch (status.trim()) {
            case PaperStatus.PENDING -> "待分配";
            case PaperStatus.ASSIGNED -> "已分配";
            case PaperStatus.CHECKING -> "待查重";
            case PaperStatus.AUDITING -> "待审核（导师初审）";
            case PaperStatus.COMPLETED -> "初审通过";
            case PaperStatus.REJECTED -> "审核不通过";
            case PaperStatus.WITHDRAWN -> "已取消";
            case PaperStatus.REVIEWING -> "评阅中";
            case PaperStatus.MID_DEFENSE -> "中期答辩";
            case PaperStatus.FINAL_DEFENSE -> "终期答辩";
            case PaperStatus.DEFENSE_PASSED -> "答辩通过";
            case PaperStatus.FINALIZED -> "定稿";
            case PaperStatus.ARCHIVED -> "已归档";
            default -> status.trim();
        };
    }
}
