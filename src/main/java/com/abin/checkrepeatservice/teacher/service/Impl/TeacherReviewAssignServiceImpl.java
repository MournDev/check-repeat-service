package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.ReviewAssignment;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.dto.ReviewSubmitReq;
import com.abin.checkrepeatservice.teacher.mapper.ReviewAssignmentMapper;
import com.abin.checkrepeatservice.teacher.service.TeacherReviewAssignService;
import com.abin.checkrepeatservice.teacher.vo.ReviewAssignVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 教师评阅工作台服务实现（论文全生命周期 P2）。
 */
@Slf4j
@Service
public class TeacherReviewAssignServiceImpl implements TeacherReviewAssignService {

    @Resource
    private ReviewAssignmentMapper reviewAssignmentMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    public Result<Page<ReviewAssignVO>> listMyAssignments(Integer status, Integer currentPage, Integer pageSize) {
        Long reviewerId = UserBusinessInfoUtils.getCurrentUserId();
        int page = currentPage == null || currentPage < 1 ? 1 : currentPage;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<ReviewAssignment> wrapper = new LambdaQueryWrapper<ReviewAssignment>()
                .eq(ReviewAssignment::getReviewerId, reviewerId)
                .orderByAsc(ReviewAssignment::getDeadline)
                .orderByDesc(ReviewAssignment::getCreateTime);
        if (status != null) {
            wrapper.eq(ReviewAssignment::getStatus, status);
        }

        Page<ReviewAssignment> result = reviewAssignmentMapper.selectPage(new Page<>(page, size), wrapper);
        List<ReviewAssignVO> voList = enrich(result.getRecords(), false);
        Page<ReviewAssignVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return Result.success("评阅任务列表查询成功", voPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ReviewAssignVO> submit(Long assignmentId, ReviewSubmitReq req) {
        Long currentTeacherId = UserBusinessInfoUtils.getCurrentUserId();

        ReviewAssignment assignment = reviewAssignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "评阅任务不存在或已删除");
        }
        if (!Objects.equals(assignment.getReviewerId(), currentTeacherId)) {
            throw new BusinessException(ResultCode.PERMISSION_TEACHER_OWNER, "仅可操作分配给自己的评阅任务");
        }
        // 已提交 → 重复提交；已超时转派 → 原任务已失效（管理员改派后生成新任务）
        if (assignment.getStatus() != null && assignment.getStatus() == ReviewAssignment.STATUS_SUBMITTED) {
            throw new BusinessException(ResultCode.BUSINESS_NO_REPEAT, "该评阅任务已提交过结果，请勿重复提交");
        }
        if (assignment.getStatus() != null && assignment.getStatus() == ReviewAssignment.STATUS_TIMEOUT) {
            throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS, "该评阅任务已超时转派，无法再提交");
        }

        // 线上/线下按 method 分流校验：线下评审不做流程分支，只做「结果回填」
        boolean online = ReviewAssignment.METHOD_ONLINE.equals(assignment.getMethod());
        if (online) {
            if (req.getScore() == null || !StringUtils.hasText(req.getComment())) {
                throw new BusinessException(ResultCode.PARAM_EMPTY, "线上评阅必须填写评分与评阅意见");
            }
        } else {
            if (req.getAttachmentId() == null) {
                throw new BusinessException(ResultCode.PARAM_EMPTY, "线下评阅必须上传评阅表扫描件");
            }
        }

        // 评阅意见 XSS 清洗（纯文本），与指导记录清洗口径一致
        ReviewAssignment update = new ReviewAssignment();
        update.setId(assignmentId);
        update.setStatus(ReviewAssignment.STATUS_SUBMITTED);
        update.setScore(req.getScore());
        update.setComment(StringUtils.hasText(req.getComment())
                ? Jsoup.clean(req.getComment(), Safelist.none()) : null);
        update.setAttachmentId(req.getAttachmentId());
        reviewAssignmentMapper.updateById(update);

        log.info("评阅结果提交成功：assignmentId={}，reviewerId={}，method={}",
                assignmentId, currentTeacherId, assignment.getMethod());

        assignment.setStatus(ReviewAssignment.STATUS_SUBMITTED);
        assignment.setScore(update.getScore());
        assignment.setComment(update.getComment());
        assignment.setAttachmentId(update.getAttachmentId());
        return Result.success("评阅结果提交成功", enrich(List.of(assignment), true).get(0));
    }

    // ------------------------------ 私有辅助方法 ------------------------------

    /**
     * 批量组装 VO：论文标题 + 学生/导师姓名（盲审脱敏）。
     *
     * @param maskBlind 教师端传 true（blind 任务脱敏学生/导师）；管理端传 false（全量可见）
     */
    private List<ReviewAssignVO> enrich(List<ReviewAssignment> assignments, boolean maskBlind) {
        if (CollectionUtils.isEmpty(assignments)) {
            return List.of();
        }

        List<Long> paperIds = assignments.stream().map(ReviewAssignment::getPaperId).distinct().toList();
        Map<Long, PaperInfo> paperMap = paperInfoMapper.selectBatchIds(paperIds).stream()
                .collect(Collectors.toMap(PaperInfo::getId, Function.identity()));

        List<Long> studentIds = paperMap.values().stream()
                .map(PaperInfo::getStudentId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> studentNameMap = studentIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));

        return assignments.stream()
                .map(a -> {
                    PaperInfo paper = paperMap.get(a.getPaperId());
                    String studentName = paper == null ? null : studentNameMap.get(paper.getStudentId());
                    return toVO(a, paper, studentName, maskBlind);
                })
                .toList();
    }

    private ReviewAssignVO toVO(ReviewAssignment assignment, PaperInfo paper,
                                String studentName, boolean maskBlind) {
        ReviewAssignVO vo = new ReviewAssignVO();
        vo.setId(assignment.getId());
        vo.setPaperId(assignment.getPaperId());
        vo.setPaperTitle(paper == null ? null : paper.getPaperTitle());
        vo.setSubmitVersion(assignment.getSubmitVersion());
        vo.setReviewerId(assignment.getReviewerId());
        vo.setReviewType(assignment.getReviewType());
        vo.setReviewTypeDesc(reviewTypeDesc(assignment.getReviewType()));
        vo.setMethod(assignment.getMethod());
        vo.setMethodDesc(ReviewAssignment.METHOD_OFFLINE.equals(assignment.getMethod()) ? "线下评阅" : "线上评阅");
        vo.setStatus(assignment.getStatus());
        vo.setStatusDesc(statusDesc(assignment.getStatus()));
        vo.setScore(assignment.getScore());
        vo.setComment(assignment.getComment());
        vo.setAttachmentId(assignment.getAttachmentId());
        vo.setDeadline(assignment.getDeadline());
        vo.setAssignType(assignment.getAssignType());
        vo.setCreateTime(assignment.getCreateTime());

        // 盲审脱敏：查询层抹掉学生与导师信息（管理端不脱敏，maskBlind=false）
        boolean masked = maskBlind && ReviewAssignment.TYPE_BLIND.equals(assignment.getReviewType());
        vo.setStudentName(masked ? null : studentName);
        vo.setTeacherName(masked ? null : (paper == null ? null : paper.getTeacherName()));
        return vo;
    }

    private String reviewTypeDesc(String reviewType) {
        if (reviewType == null) {
            return null;
        }
        return switch (reviewType) {
            case ReviewAssignment.TYPE_ADVISOR -> "导师评阅";
            case ReviewAssignment.TYPE_BLIND -> "盲审";
            default -> "交叉评阅";
        };
    }

    private String statusDesc(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case ReviewAssignment.STATUS_PENDING -> "待评阅";
            case ReviewAssignment.STATUS_IN_PROGRESS -> "评阅中";
            case ReviewAssignment.STATUS_SUBMITTED -> "已提交";
            case ReviewAssignment.STATUS_TIMEOUT -> "已超时转派";
            default -> String.valueOf(status);
        };
    }
}
