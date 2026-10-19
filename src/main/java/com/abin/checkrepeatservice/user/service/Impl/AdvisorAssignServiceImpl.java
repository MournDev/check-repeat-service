package com.abin.checkrepeatservice.user.service.Impl;

import cn.hutool.core.date.DateTime;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.notification.service.NotificationService;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.user.mapper.TeacherAllocationRecordMapper;
import com.abin.checkrepeatservice.user.service.AdvisorAssignService;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.abin.checkrepeatservice.user.vo.PaperAdvisorTaskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 指导老师分配服务实现（含任务数更新）
 */
@Service
@Slf4j
public class AdvisorAssignServiceImpl implements AdvisorAssignService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private TeacherAllocationRecordMapper teacherAllocationRecordMapper;

    @Resource
    private NotificationFacadeService notificationFacadeService;

    @Resource
    private NotificationService notificationService;

    @Resource
    private EmailNotificationService emailNotificationService;

    @Resource
    private MessageService messageService;



    private final UserBusinessInfoUtils userBusinessInfoUtils;

    @Value("${advisor-assign.max-task-count}")
    private Integer maxTaskCount;

    @Value("${advisor-assign.retry-count}")
    private Integer retryCount;

    public AdvisorAssignServiceImpl(UserBusinessInfoUtils userBusinessInfoUtils) {
        this.userBusinessInfoUtils = userBusinessInfoUtils;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> autoAssignAdvisor(Long paperId) {
        return autoAssignAdvisor(paperId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> autoAssignAdvisor(Long paperId, Long excludeTeacherId) {
        log.info("开始自动分配指导老师 - 论文ID: {}, 排除教师: {}", paperId, excludeTeacherId);

        try {
            // 1. 查询论文基本信息
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在");
            }
            // 2. 检查是否已经分配过老师
            if (paperInfo.getTeacherId() != null) {
                return Result.success("论文已分配老师", true);
            }
            log.info("论文详细信息 - ID: {}, 标题: {}, 学生ID: {}, 老师ID: {}",
                    paperInfo.getId(), paperInfo.getPaperTitle(), paperInfo.getStudentId(), paperInfo.getTeacherId());
            // 2. 检查论文状态是否允许分配老师
            String paperStatus = paperInfo.getPaperStatus();
            if (!DictConstants.PaperStatus.PENDING.equals(paperInfo.getPaperStatus())) {
                return Result.error(ResultCode.BUSINESS_TASK_ASSIGNED,  // ✅ 改为返回 Result.error
                        "论文状态不允许分配老师，当前状态：" + paperStatus);
            }

            String paperTitle = paperInfo.getPaperTitle() != null ? paperInfo.getPaperTitle() : "";
            Long studentMajorId = paperInfo.getMajorId();
            Long studentId = paperInfo.getStudentId();

            // 多轮重试分配
            for (int i = 0; i < retryCount; i++) {
                log.info("第 {} 轮尝试分配指导老师 - 论文ID: {}", i + 1, paperId);

                // 查询符合条件的老师（排除指定教师，如刚拒绝分配的那位）
                List<PaperAdvisorTaskVO> eligibleAdvisors = queryEligibleAdvisors(studentMajorId, excludeTeacherId);
                if (eligibleAdvisors.isEmpty()) {
                    log.warn("第 {} 轮未找到符合条件的老师 - 论文ID: {}", i + 1, paperId);
                    continue;
                }

                // 按优先级排序
                List<PaperAdvisorTaskVO> sortedAdvisors = sortAdvisorsByPriority(eligibleAdvisors, studentMajorId, paperTitle);
                if (sortedAdvisors.isEmpty()) {
                    log.warn("第 {} 轮排序后无合适老师 - 论文ID: {}", i + 1, paperId);
                    continue;
                }

                // 选择最优老师
                PaperAdvisorTaskVO bestAdvisor = sortedAdvisors.get(0); // 替代 getFirst()
                // 4. 更新论文信息中的指导老师信息
                PaperInfo updatePaper = new PaperInfo();
                updatePaper.setId(paperId);
                updatePaper.setTeacherId(bestAdvisor.getAdvisorId());
                updatePaper.setTeacherName(bestAdvisor.getAdvisorName());
                updatePaper.setAllocationType(DictConstants.AllocationType.AUTO);
                updatePaper.setAllocationStatus(DictConstants.AllocationStatus.PENDING);//待确认
                updatePaper.setAllocationTime(LocalDateTime.now());
                updatePaper.setPaperStatus(DictConstants.PaperStatus.ASSIGNED);//已分配
                updatePaper.setUpdateTime(LocalDateTime.now());

                int updateResult = paperInfoMapper.updateById(updatePaper);
                if (updateResult <= 0) {
                    log.error("更新论文指导老师信息失败 - 论文ID: {}", paperId);
                    continue;
                }

                // 5. 更新老师任务数
                SysUser advisor = sysUserMapper.selectById(bestAdvisor.getAdvisorId());
                if (advisor != null) {
                    advisor.setCurrentAdvisorCount(advisor.getCurrentAdvisorCount() + 1);
                    sysUserMapper.updateById(advisor);
                }

                // 6. 创建分配记录
                createAllocationRecord(paperId, studentId, bestAdvisor.getAdvisorId());

                // 7. 推送通知（使用邮件通知服务）
                sendAssignmentNotifications(paperId, paperInfo.getPaperTitle(), studentId, bestAdvisor.getAdvisorId());

                log.info("分配指导老师成功 - 论文ID: {}, 老师ID: {}, 老师姓名: {}",
                        paperId, bestAdvisor.getAdvisorId(), bestAdvisor.getAdvisorName());
                return Result.success();
            }
            // 所有重试都失败
            log.error("自动分配指导老师失败，经过 {} 轮重试仍未成功 - 论文ID: {}", retryCount, paperId);
            return Result.error(ResultCode.SYSTEM_ERROR, "自动分配失败，请联系管理员手动分配");
        } catch (Exception e) {
            log.error("自动分配指导老师异常 - 论文ID: {}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "指导老师分配过程发生异常：" + "系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> manualAssignAdvisor(Long paperId, Long teacherId, String reason) {
        log.info("手动分配指导老师 - 论文ID: {}, 老师ID: {}, 原因: {}", paperId, teacherId, reason);
        // 校验老师合法性
        SysUser advisor = sysUserMapper.selectById(teacherId);
        if ((advisor == null) || !"2001".equals(advisor.getRoleId())) {
            throw new BusinessException(ResultCode.BUSINESS_ILLEGAL, "指导老师不合法", null);
        }
        if (advisor.getCurrentAdvisorCount() >= maxTaskCount) {
            throw new BusinessException(ResultCode.BUSINESS_NO_TASK_MAX, "老师任务数已达上限", null);
        }

        // 更新论文信息
        PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
        if (paperInfo == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "论文不存在", paperId);
        }
        paperInfo.setTeacherId(teacherId);
        paperInfo.setTeacherName(advisor.getRealName());//指导老师姓名
        paperInfo.setAllocationType(DictConstants.AllocationType.MANUAL);//手动指定
        paperInfo.setAllocationStatus(DictConstants.AllocationStatus.PENDING);//待确认
        paperInfo.setAllocationTime(LocalDateTime.now());
        paperInfo.setPaperStatus(DictConstants.PaperStatus.ASSIGNED);//已分配，等待学生确认
        paperInfo.setUpdateTime(LocalDateTime.now());
        // 落库。原来这个方法从头到尾没有 updateById：paperInfo 只在内存里改完就丢了，
        // 接口照样返回「手动分配指导老师成功」，实际只有老师负载 +1 和一条分配记录，
        // 论文上根本没有导师（待分配列表里还在、学生端还是"暂未分配"）。
        paperInfoMapper.updateById(paperInfo);
        // 更新老师任务数
        advisor.setCurrentAdvisorCount(advisor.getCurrentAdvisorCount() + 1);
        sysUserMapper.updateById(advisor);


        // 创建分配记录
        createAllocationRecord(paperId, paperInfo.getStudentId(), teacherId,
                DictConstants.AllocationType.MANUAL, reason, getCurrentOperatorId());

        // 发送通知（教师待确认 + 学生分配结果）。前端教师端「待确认」唯一入口是消息中心，
        // 不发通知教师将完全无感知；此前此处被注释导致手动分配后师生均收不到站内信。
        sendAssignmentNotifications(paperId, paperInfo.getPaperTitle(), paperInfo.getStudentId(), teacherId);

        log.info("手动分配指导老师成功 - 论文ID: {}, 老师ID: {}, 老师姓名: {}",
                paperId, teacherId, advisor.getRealName());
        return Result.success("手动分配指导老师成功");
    }


    /**
     * 获取当前操作者ID（使用UserBusinessInfoUtils）
     */
    private Long getCurrentOperatorId() {
        try {
            return UserBusinessInfoUtils.getCurrentUserId();
        } catch (Exception e) {
            log.warn("获取当前用户ID失败，使用默认管理员ID。错误信息: {}", e.getMessage());
            return 1L; // 失败时返回默认管理员ID
        }
    }

    /**
     * 发送分配通知（邮箱和站内信分开发送）
     */
    private void sendAssignmentNotifications(Long paperId, String paperTitle, Long studentId, Long advisorId) {
        try {
            // 1. 发送邮箱通知（独立发送，互不影响）
            //emailNotificationService.sendTeacherAssignmentEmail(paperId, paperTitle, studentId, advisorId);

            // 2. 发送站内信通知（独立发送，互不影响）
            notificationService.sendTeacherAssignmentNotice(paperId, paperTitle, studentId, advisorId);
            notificationService.sendStudentAssignmentNotice(paperId, paperTitle, studentId, advisorId);

            log.info("分配通知发送完成 - 论文ID: {}, 学生ID: {}, 老师ID: {}",
                    paperId, studentId, advisorId);

        } catch (Exception e) {
            log.error("发送分配通知失败 - 论文ID: {}, 学生ID: {}, 老师ID: {}",
                    paperId, studentId, advisorId, e);
        }
    }


    /**
     * 查询符合条件的指导老师
     */
    private List<PaperAdvisorTaskVO> queryEligibleAdvisors(Long studentMajorId, Long excludeTeacherId) {
        List<SysUser> teachers = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getRoleId, 2001L)  // 修复：使用Long类型而不是字符串
                        .lt(SysUser::getCurrentAdvisorCount, maxTaskCount)
                        .eq(SysUser::getIsDeleted, 0)
                        .ne(excludeTeacherId != null, SysUser::getId, excludeTeacherId)
        );

        return teachers.stream().map(teacher -> {
            PaperAdvisorTaskVO vo = new PaperAdvisorTaskVO();
            vo.setAdvisorId(teacher.getId());
            vo.setAdvisorName(teacher.getRealName());
            vo.setMajorId(teacher.getMajorId());
            vo.setResearchDirection(teacher.getResearchDirection());
            vo.setCurrentTaskCount(teacher.getCurrentAdvisorCount());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 按优先级排序老师
     */
    private List<PaperAdvisorTaskVO> sortAdvisorsByPriority(
            List<PaperAdvisorTaskVO> advisors, Long studentMajorId, String paperTitle) {
        String paperKeyword = getKeyword(paperTitle);
        return advisors.stream()
                // 优先级1：专业匹配
                .sorted(
                        // 优先级1：专业匹配（Comparator）
                        Comparator.<PaperAdvisorTaskVO>comparingInt(vo -> isMajorMatch(vo.getMajorId(), studentMajorId) ? 0 : 1)
                                // 优先级2：研究方向匹配（链式调用 thenComparingInt，属于 Comparator）
                                .thenComparingInt(vo -> isDirectionMatch(vo.getResearchDirection(), paperKeyword) ? 0 : 1)
                                // 优先级3：负载均衡（链式调用 thenComparingInt，属于 Comparator）
                                .thenComparingInt(PaperAdvisorTaskVO::getCurrentTaskCount)
                )
                .collect(Collectors.toList());
    }
    private boolean isMajorMatch(Long teacherMajorId, Long studentMajorId) {
        return studentMajorId != null && teacherMajorId != null && teacherMajorId.equals(studentMajorId);
    }

    private boolean isDirectionMatch(String researchDirection, String paperKeyword) {
        return paperKeyword != null && !paperKeyword.isEmpty()
                && researchDirection != null && !researchDirection.isEmpty()
                && researchDirection.toLowerCase().contains(paperKeyword.toLowerCase());
    }

    /**
     * 从论文标题提取关键词
     */
    private String getKeyword(String title) {
        String[] keywords = {"Spring Boot", "论文查重", "管理系统", "数据分析", "机器学习"};
        for (String keyword : keywords) {
            if (title.contains(keyword)) return keyword;
        }
        return "";
    }

    /**
     * 推送分配通知（简化版）
     */
    private void sendNotification(Long advisorId, Long paperId) {
        SysUser advisor = sysUserMapper.selectById(advisorId);
        String message = String.format("您已分配到新论文指导任务（论文ID：%d），请及时审核", paperId);
        System.out.println("通知老师[" + advisor.getRealName() + "]：" + message);
        // 实际项目集成站内信/邮件服务
    }



    /**
     * 获取分配类型标签
     */
    private String getAllocationTypeLabel(String allocationType) {
        switch (allocationType) {
            case DictConstants.AllocationType.AUTO:
                return "自动分配";
            case DictConstants.AllocationType.MANUAL:
                return "手动分配";
            default:
                return "未知类型";
        }
    }
    /**
     * 获取分配状态标签
     */
    private String getAllocationStatusLabel(String allocationStatus) {
        switch (allocationStatus) {
            case DictConstants.AllocationStatus.PENDING:
                return "待确认";
            case DictConstants.AllocationStatus.CONFIRMED:
                return "已确认";
            case DictConstants.AllocationStatus.REJECTED:
                return "已拒绝";
            default:
                return "未知状态";
        }
    }

    /**
     * 创建指导老师分配记录
     */
    private void createAllocationRecord(Long paperId, Long studentId, Long teacherId) {
        createAllocationRecord(paperId, studentId, teacherId, DictConstants.AllocationType.AUTO, null, null);
    }

    /**
     * 创建指导老师分配记录（完整参数）
     */
    private void createAllocationRecord(Long paperId, Long studentId, Long teacherId,
                                        String allocationType, String reason, Long operatorId) {
        try {
            TeacherAllocationRecord record = new TeacherAllocationRecord();
            record.setPaperId(paperId);
            record.setStudentId(studentId);
            record.setTeacherId(teacherId);
            record.setAllocationType(allocationType);
            record.setAllocationReason(reason);
            record.setAllocationTime(LocalDateTime.now()); // 替代 DateTime.now()
            record.setOperatorId(operatorId);
            record.setCreateTime(LocalDateTime.now()); // 替代 DateTime.now()

            int result = teacherAllocationRecordMapper.insert(record);

            if (result > 0) {
                log.debug("创建指导老师分配记录成功 - 记录ID: {}, 论文ID: {}, 老师ID: {}",
                        record.getId(), paperId, teacherId);
            } else {
                log.warn("创建指导老师分配记录失败 - 论文ID: {}, 老师ID: {}", paperId, teacherId);
            }
        } catch (Exception e) {
            log.warn("创建指导老师分配记录异常 - 论文ID: {}, 老师ID: {}", paperId, teacherId, e);
        }
    }
}