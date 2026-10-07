package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.notification.service.NotificationService;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.CheckTaskService;
import com.abin.checkrepeatservice.teacher.service.TeacherAssignmentService;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

    @Resource
    private NotificationService notificationService;

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
        return doRejectAssignment(paperId, teacherId, false);
    }

    @Override
    public Result<Boolean> rejectAssignmentByTimeout(Long paperId, Long teacherId) {
        return doRejectAssignment(paperId, teacherId, true);
    }

    /**
     * 拒绝接收论文的公共实现
     *
     * 手动拒绝与超时自动拒绝在数据上的处理完全一致（都是 REJECTED + 论文回到待分配），
     * 唯一差别是发给学生的通知模板，所以不再各写一份。
     *
     * @param byTimeout true=超时自动拒绝（定时任务触发，老师并未操作）
     */
    private Result<Boolean> doRejectAssignment(Long paperId, Long teacherId, boolean byTimeout) {
        String action = byTimeout ? "超时自动拒绝论文" : "教师拒绝接收论文";
        try {
            // 1. 验证权限和状态
            PaperInfo paperInfo = validateTeacherPermission(paperId, teacherId);
            if (!DictConstants.AllocationStatus.PENDING.equals(paperInfo.getAllocationStatus())) {
                return Result.error(ResultCode.BUSINESS_TASK_ASSIGNED, "论文分配状态不允许拒绝");
            }
            // 2. 更新论文分配状态
            // 退回待分配必须把 teacher_id 一起清掉：管理员待分配列表、自动分配、学生端
            // 「我有没有导师」全都只看 teacher_id 是否为空（见 autoAssignAdvisor 开头的
            // 「论文已分配老师」判断）。只把 paper_status 改回 pending 的话，这篇论文会永远
            // 停在「已分配给那位拒绝它的老师」的状态——进不了待分配列表，也无法被重新分配。
            // 注意：updateById 默认忽略 null 字段，所以清空教师必须用 UpdateWrapper 显式 set。
            LambdaUpdateWrapper<PaperInfo> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(PaperInfo::getId, paperId)
                         .set(PaperInfo::getAllocationStatus, DictConstants.AllocationStatus.REJECTED)
                         .set(PaperInfo::getPaperStatus, DictConstants.PaperStatus.PENDING)
                         .set(PaperInfo::getTeacherId, null)
                         .set(PaperInfo::getTeacherName, null)
                         .set(PaperInfo::getUpdateTime, LocalDateTime.now());
            paperInfoMapper.update(null, updateWrapper);

            // 3. 释放教师任务数
            releaseTeacherTaskCount(teacherId);

            // 4. 发送拒绝通知给学生
            if (byTimeout) {
                notificationService.sendTimeoutRejectNotice(
                        paperId, paperInfo.getStudentId(), teacherId);
            } else {
                sendConfirmationNoticeToStudent(paperId, teacherId, false);
            }

            // 5. 记录拒绝操作
            //createConfirmationRecord(paperId, teacherId, DictConstants.AllocationStatus.REJECTED);

            log.info("{} - 论文ID: {}, 老师ID: {}", action, paperId, teacherId);
            return Result.success(byTimeout ? "超时自动拒绝成功" : "拒绝接收成功", true);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("{}异常 - 论文ID: {}, 老师ID: {}", action, paperId, teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, byTimeout ? "超时自动拒绝失败" : "拒绝接收失败");
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
     * 发送确认/拒绝结果通知给学生
     *
     * 本方法是 TEACHER_CONFIRM_NOTICE / TEACHER_REJECT_NOTICE 这两个模板编码唯一的发送方
     * （原先另有一套同名的确认/拒绝通知方法，与这里完全重复且无调用点，已随
     * InternalMessageNotificationService 一并删除）。传给模板的变量集：
     *   TEACHER_CONFIRM_NOTICE：studentName、paperTitle、teacherName、confirmTime
     *   TEACHER_REJECT_NOTICE ：studentName、paperTitle、teacherName、rejectReason、rejectTime
     * 改这里或改种子脚本里的模板时，两边要一起动。
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
                // 拒绝原因没有入参（rejectAssignment 也没收这个参数），写句兜底文案，
                // 免得模板里留下未替换的 {rejectReason}
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
