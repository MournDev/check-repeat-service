package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.CheckTaskService;
import com.abin.checkrepeatservice.teacher.service.TeacherAssignmentService;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class TeacherAssignmentServiceImpl implements TeacherAssignmentService {

    @Resource
    private MessageService messageService;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private CheckTaskService checkTaskService;

    @Override
    public Result<Boolean> confirmAssignment(Long paperId, Long teacherId) {
        try {
            // 1. 验证权限和状态
            PaperInfo paperInfo = validateTeacherPermission(paperId, teacherId);
            if (!DictConstants.AllocationStatus.PENDING.equals(paperInfo.getAllocationStatus())) {
                return Result.error(ResultCode.BUSINESS_TASK_ASSIGNED, "论文分配状态不允许确认");
            }

            // 2. 更新论文分配状态
            PaperInfo updatePaper = new PaperInfo();
            updatePaper.setId(paperId);
            updatePaper.setAllocationStatus(DictConstants.AllocationStatus.CONFIRMED);
            updatePaper.setPaperStatus(DictConstants.PaperStatus.CHECKING);  // 进入待查重状态
            updatePaper.setConfirmTime(LocalDateTime.now());
            updatePaper.setUpdateTime(LocalDateTime.now());
            paperInfoMapper.updateById(updatePaper);

            // 3. 发送确认通知给学生
            sendConfirmationNoticeToStudent(paperId, teacherId, true);

            // 4. 记录确认操作
            //createConfirmationRecord(paperId, teacherId, DictConstants.AllocationStatus.CONFIRMED, null);

            // 5. 触发查重逻辑
            triggerCheckRepeatLogic(paperId);
            log.info("教师确认接收论文成功 - 论文ID: {}, 老师ID: {}", paperId, teacherId);
            return Result.success( "确认接收成功", true);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("教师确认接收论文异常 - 论文ID: {}, 老师ID: {}", paperId, teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "确认接收失败");
        }
    }
    /**
     * 触发查重逻辑
     */
    private void triggerCheckRepeatLogic(Long paperId) {
        try {
            // 调用查重服务创建查重任务
            checkTaskService.createCheckTask(paperId);
            log.info("查重任务创建成功 - 论文ID: {}", paperId);
        } catch (Exception e) {
            log.error("触发查重逻辑失败 - 论文ID: {}", paperId, e);
            // 查重触发失败不应影响主流程，仅记录日志
        }
    }


    @Override
    public Result<Boolean> rejectAssignment(Long paperId, Long teacherId) {
        try {
            // 1. 验证权限和状态
            PaperInfo paperInfo = validateTeacherPermission(paperId, teacherId);
            if (!DictConstants.AllocationStatus.PENDING.equals(paperInfo.getAllocationStatus())) {
                return Result.error(ResultCode.BUSINESS_TASK_ASSIGNED, "论文分配状态不允许拒绝");
            }
            // 2. 更新论文分配状态
            PaperInfo updatePaper = new PaperInfo();
            updatePaper.setId(paperId);
            updatePaper.setAllocationStatus(DictConstants.AllocationStatus.REJECTED);
            updatePaper.setPaperStatus(DictConstants.PaperStatus.PENDING);  // 返回待分配状态，重新分配
            updatePaper.setUpdateTime(LocalDateTime.now());
            paperInfoMapper.updateById(updatePaper);

            // 3. 释放教师任务数
            releaseTeacherTaskCount(teacherId);

            // 4. 发送拒绝通知给学生
            sendConfirmationNoticeToStudent(paperId, teacherId, false);

            // 5. 记录拒绝操作
            //createConfirmationRecord(paperId, teacherId, DictConstants.AllocationStatus.REJECTED);

            log.info("教师拒绝接收论文 - 论文ID: {}, 老师ID: {}", paperId, teacherId);
            return Result.success( "拒绝接收成功", true);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("教师拒绝接收论文异常 - 论文ID: {}, 老师ID: {}", paperId, teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "拒绝接收失败");
        }
    }

    @Override
    public Result<Page<PaperInfo>> getPendingConfirmPapers(Long teacherId, Integer pageNum, Integer pageSize) {
        try {
            if (teacherId == null) {
                return Result.error(ResultCode.PARAM_ERROR, "教师ID不能为空");
            }

            Page<PaperInfo> page = new Page<>(pageNum, pageSize);

            LambdaQueryWrapper<PaperInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PaperInfo::getTeacherId, teacherId)
                    .eq(PaperInfo::getAllocationStatus, DictConstants.AllocationStatus.PENDING)
                    .orderByDesc(PaperInfo::getAllocationTime);

            Page<PaperInfo> paperPage = paperInfoMapper.selectPage(page, queryWrapper);

            return Result.success( "获取待确认论文列表成功",paperPage);

        } catch (Exception e) {
            log.error("获取待确认论文列表失败 - 教师ID: {}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取待确认论文列表失败");
        }
    }

    /**
     * 验证教师权限
     */
    private PaperInfo validateTeacherPermission(Long paperId, Long teacherId) {
        PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
        if (paperInfo == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文不存在");
        }
        if (!teacherId.equals(paperInfo.getTeacherId())) {
            throw new BusinessException(ResultCode.BUSINESS_ILLEGAL, "无权限操作此论文");
        }
        return paperInfo;
    }

    /**
     * 释放教师任务数
     */
    private void releaseTeacherTaskCount(Long teacherId) {
        try {
            SysUser teacher = sysUserMapper.selectById(teacherId);
            if (teacher != null && teacher.getCurrentAdvisorCount() > 0) {
                teacher.setCurrentAdvisorCount(teacher.getCurrentAdvisorCount() - 1);
                sysUserMapper.updateById(teacher);
                log.info("释放教师任务数成功 - 老师ID: {}", teacherId);
            }
        } catch (Exception e) {
            log.error("释放教师任务数失败 - 老师ID: {}", teacherId, e);
        }
    }

    /**
     * 发送确认结果通知给学生
     *
     * TEACHER_CONFIRM_NOTICE / TEACHER_REJECT_NOTICE 这两个模板编码各有两个发送方，
     * 本方法与 InternalMessageNotificationService.sendTeacherConfirmNotice /
     * sendTeacherRejectNotice 传的变量名必须完全一致，否则模板里引用了任何一方的
     * 私有键，另一个发送方就会渲染出未替换的占位符。统一后的变量集：
     *   TEACHER_CONFIRM_NOTICE：studentName、paperTitle、teacherName、confirmTime
     *   TEACHER_REJECT_NOTICE ：studentName、paperTitle、teacherName、rejectReason、rejectTime
     */
    private void sendConfirmationNoticeToStudent(Long paperId, Long teacherId, boolean confirmed) {
        try {
            PaperInfo paper = paperInfoMapper.selectById(paperId);
            SysUser teacher = sysUserMapper.selectById(teacherId);

            if (paper == null || teacher == null) {
                return;
            }

            SysUser student = sysUserMapper.selectById(paper.getStudentId());
            if (student == null) {
                return;
            }

            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paper.getPaperTitle());
            params.put("teacherName", teacher.getRealName());

            String templateCode;
            if (confirmed) {
                templateCode = "TEACHER_CONFIRM_NOTICE";
                params.put("confirmTime", now);
            } else {
                templateCode = "TEACHER_REJECT_NOTICE";
                // 拒绝原因没有入参（rejectAssignment 也没收），与 InternalMessageNotificationService 的兜底文案保持一致
                params.put("rejectReason", "未提供原因");
                params.put("rejectTime", now);
            }

            Result<Boolean> sendResult = messageService.sendMessageByTemplate(
                    templateCode,
                    paper.getStudentId(),
                    paperId,
                    DictConstants.RelatedType.PAPER,
                    params
            );

            if (sendResult.isSuccess()) {
                log.info("发送确认结果通知给学生成功 - 论文ID: {}, 学生ID: {}", paperId, paper.getStudentId());
            } else {
                log.warn("发送确认结果通知给学生失败 - 论文ID: {}, 错误: {}", paperId, sendResult.getMessage());
            }

        } catch (Exception e) {
            log.error("发送确认结果通知给学生异常 - 论文ID: {}, 老师ID: {}", paperId, teacherId, e);
        }
    }
}
