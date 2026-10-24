package com.abin.checkrepeatservice.student.service.Impl;


import com.abin.checkrepeatservice.admin.service.CollegeQuotaService;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.engine.CheckEngineManager;
import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.statemachine.CheckTaskStateMachine;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.student.dto.CheckTaskResultDTO;
import com.abin.checkrepeatservice.student.event.CheckTaskCreatedEvent;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.CheckTaskService;
import com.abin.checkrepeatservice.student.service.CheckTaskValidationService;
import com.abin.checkrepeatservice.user.vo.CheckResultVO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 查重任务服务实现类
 */
@Slf4j
@Service
public class CheckTaskServiceImpl extends ServiceImpl<CheckTaskMapper, CheckTask> implements CheckTaskService {

    @Resource
    private PaperInfoMapper paperInfoMapper;

    // M3.1 版本—报告绑定：任务落库时快照论文版本与最新提交记录
    @Resource
    private com.abin.checkrepeatservice.admin.mapper.PaperSubmitMapper paperSubmitMapper;

    @Resource
    private CheckReportMapper checkReportMapper;

    @Resource
    private CheckTaskStateMachine stateMachine;

    // M2.2 阶段门收口：论文状态流转统一校验（默认影子模式，只告警不阻断）
    @Resource
    private com.abin.checkrepeatservice.common.component.PaperStageGate paperStageGate;

    @Resource
    private CheckEngineManager checkEngineManager;

    @Resource
    private CheckTaskValidationService validationService;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    /**
     * 显式事务模板（P0-2）：createCheckTask 内部原本用 this 调用 @Transactional 方法，
     * 绕过 Spring 代理导致事务失效。这里用编程式事务把「扣额度 + 落任务 + 发事件」
     * 包成同一个事务，事件由 @TransactionalEventListener(AFTER_COMMIT) 在提交后消费。
     */
    @Resource
    private TransactionTemplate transactionTemplate;

    // P5 学院月度查重额度：新建任务落库前校验并扣减
    @Resource
    private CollegeQuotaService collegeQuotaService;

    @Override
    public Result<CheckResultVO> createCheckTask(Long paperId) {
        return createCheckTask(paperId, null);
    }

    /**
     * 创建查重任务（P0-2 / P0-8）。
     *
     * <p>事务边界：前置校验放在事务外；「扣额度 + 任务落库 + 发布事件」由
     * TransactionTemplate 显式包裹，避免同类 this 调用绕过 @Transactional。
     * 事件由监听器的 @TransactionalEventListener(AFTER_COMMIT) 在提交后异步消费。</p>
     *
     * @param studentId 论文归属学生；教师代触发时显式传入，学生自助时为 null
     */
    @Override
    public Result<CheckResultVO> createCheckTask(Long paperId, Long studentId) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        // 归属校验主体：显式传入的学生优先，否则当前登录用户
        Long ownerId = studentId != null ? studentId : currentUserId;

        // 前置校验（在事务外执行）
        log.info("开始创建查重任务 - 论文 ID: {}, 发起人 ID: {}, 归属学生 ID: {}", paperId, currentUserId, ownerId);
        CheckTaskValidationService.ValidationResult validationResult =
            validationService.validateCheckRequest(paperId, ownerId);

        if (!validationResult.isSuccess()) {
            log.warn("查重前置校验失败 - 论文 ID: {}, 原因：{}", paperId, validationResult.getMessage());
            return Result.error(ResultCode.BUSINESS_NO_REPEAT, validationResult.getMessage());
        }

        PaperInfo paperInfo = validationResult.getPaper();

        try {
            CheckResultVO checkResultVO = transactionTemplate.execute(status ->
                    createCheckTaskInTransaction(paperId, paperInfo, ownerId));
            if (checkResultVO == null) {
                return Result.error(ResultCode.SYSTEM_ERROR, "查重任务创建失败：事务未返回结果");
            }

            log.info("查重任务创建成功 - 任务 ID: {}, 论文 ID: {}, 预估时长：{}秒",
                    checkResultVO.getTaskId(), paperId, estimateDuration(paperInfo.getWordCount()));

            return Result.success("查重任务创建成功，预计" + estimateDuration(paperInfo.getWordCount()) +
                                "秒内完成（当前排队位置：#" + getQueuePosition() + "）", checkResultVO);
        } catch (BusinessException e) {
            // 额度不足等业务异常：保留原错误码（409），不再统一抹成 500
            log.warn("创建查重任务业务校验失败 - 论文 ID: {}, 原因：{}", paperId, e.getMessage());
            return Result.error(e.getResultCode(), e.getMessage());
        } catch (Exception e) {
            log.error("创建查重任务失败 - 论文 ID: {}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查重任务创建失败：系统异常，请稍后重试");
        }
    }

    /**
     * 在 TransactionTemplate 中执行的数据库操作：扣额度、落任务、发事件。
     * 任何一步抛异常都会回滚额度扣减，避免「扣了额度却没有任务」。
     */
    private CheckResultVO createCheckTaskInTransaction(Long paperId, PaperInfo paperInfo, Long ownerId) {
        // P5 学院月度额度：任务真正落库前校验并扣减学院当月额度。
        // 重试路径复用已存在任务（重新入队），不走本新建入口，不会重复扣减。
        // M4.1：记录本次是否实际扣减，失败返还时据此幂等退回
        boolean quotaConsumed = collegeQuotaService.checkAndConsume(paperInfo.getCollegeId());

        // 1. 创建 PENDING 状态的任务（不立即改为 CHECKING）
        CheckTask checkTask = new CheckTask();
        checkTask.setPaperId(paperId);
        checkTask.setFileId(paperInfo.getFileId());
        checkTask.setTaskNo(generateTaskNo());
        checkTask.setCheckStatus(DictConstants.CheckStatus.PENDING); // 保持待处理状态
        checkTask.setQuotaCharged(quotaConsumed ? 1 : 0);
        // M3.1 版本—报告绑定：快照论文版本 + 最新一次提交记录
        checkTask.setPaperVersion(paperInfo.getVersion());
        checkTask.setSubmitId(resolveLatestSubmitId(paperId));
        // 教师代触发时 create_by 仍归到论文所属学生，与表注释/归属查询口径一致；
        // 显式赋值后 MyMetaObjectHandler 的 strictInsertFill 不会覆盖非空值
        checkTask.setCreateBy(ownerId);
        UserBusinessInfoUtils.setAuditField(checkTask, true);
        save(checkTask);

        // 2. 发布事件，事务提交后由 @TransactionalEventListener(AFTER_COMMIT) 异步执行
        eventPublisher.publishEvent(new CheckTaskCreatedEvent(this, checkTask.getId(), paperId));

        // 3. 返回 VO，真正响应在事务提交后组装
        return buildCheckResultVO(checkTask);
    }
    
    /**
     * 预估查重时长（基于字数）
     */
    private int estimateDuration(Integer wordCount) {
        if (wordCount == null || wordCount == 0) {
            return 60; // 默认 60 秒
        }
        // 每 1000 字约需 5 秒
        return Math.max(30, (int)(wordCount / 1000.0 * 5));
    }
    
    /**
     * 获取当前排队位置
     */
    private int getQueuePosition() {
        return (int)count(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.PENDING)
                .eq(CheckTask::getIsDeleted, 0)
        ) + 1;
    }
    
    /**
     * 构建返回结果 VO
     */
    private CheckResultVO buildCheckResultVO(CheckTask checkTask) {
        CheckResultVO vo = new CheckResultVO();
        vo.setTaskId(checkTask.getId());
        vo.setPaperId(checkTask.getPaperId());
        vo.setCheckStatus(checkTask.getCheckStatus());
        // M1.1 检测边界明示：建任务响应即声明检测范围
        vo.setCheckScope(CheckEngineManager.CHECK_SCOPE);
        vo.setCreateTime(checkTask.getCreateTime());
        return vo;
    }

    @Override
    public Result<Page<CheckTaskResultDTO>> getMyCheckTaskList(Long paperId, Integer checkStatus,
                                                               Integer page, Integer size) {
        int current = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 10 : Math.min(size, 100);

        SysUser currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        LambdaQueryWrapper<CheckTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CheckTask::getIsDeleted, 0)
                .orderByDesc(CheckTask::getCreateTime);

        // 1. 按角色过滤数据
        // S11 安全加固：inSql 字符串拼接改为 apply {0} 参数绑定（统一模式，防复制扩散）
        if (UserBusinessInfoUtils.isStudent()) {
            // 学生：仅查询自己论文的任务（需关联论文表的student_id）
            queryWrapper.apply("paper_id IN (SELECT id FROM paper_info WHERE student_id = {0} AND is_deleted = 0)", currentUser.getId());
        } else if (UserBusinessInfoUtils.isTeacher()) {
            // 教师：仅查询自己指导论文的任务（需关联论文表的teacher_id）
            queryWrapper.apply("paper_id IN (SELECT id FROM paper_info WHERE teacher_id = {0} AND is_deleted = 0)", currentUser.getId());
        }
        // 管理员：无过滤

        // 2. 按论文ID过滤（可选）
        if (paperId != null) {
            queryWrapper.eq(CheckTask::getPaperId, paperId);
        }

        // 3. 按任务状态过滤（可选）
        // check_status 是 varchar，不能用 Integer 直接比较：MySQL 会把字符串转数字，
        // "pending"/"checking" 等都会变成 0，导致按 0 过滤命中全部状态。
        if (checkStatus != null) {
            String statusCode = switch (checkStatus) {
                case 0 -> DictConstants.CheckStatus.PENDING;
                case 1 -> DictConstants.CheckStatus.CHECKING;
                case 2 -> DictConstants.CheckStatus.COMPLETED;
                case 3 -> DictConstants.CheckStatus.FAILURE;
                case 4 -> DictConstants.CheckStatus.CANCELLED;
                default -> null;
            };
            if (statusCode != null) {
                queryWrapper.eq(CheckTask::getCheckStatus, statusCode);
            }
        }

        // 4. 分页查询 + 批量装配（避免逐条 selectById 的 N+1）
        Page<CheckTask> taskPage = page(new Page<>(current, pageSize), queryWrapper);
        List<CheckTaskResultDTO> dtoList = convertToTaskResultDTOs(taskPage.getRecords());

        Page<CheckTaskResultDTO> resultPage = new Page<>(
                taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        resultPage.setRecords(dtoList);
        return Result.success("查重任务列表查询成功", resultPage);
    }

    @Override
    public Result<CheckTaskResultDTO> getCheckTaskDetail(Long paperId) {
        // 1. 查询任务信息
        LambdaQueryWrapper<CheckTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CheckTask::getPaperId, paperId)
                .eq(CheckTask::getIsDeleted, 0)
                .orderByDesc(CheckTask::getCreateTime);
        List<CheckTask> checkTasks = list(queryWrapper);
        CheckTask checkTask = checkTasks.isEmpty() ? null : checkTasks.get(0); // 替代 getFirst()
        if (checkTask == null || checkTask.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "查重任务不存在或已删除");
        }

        // 2. 权限校验（学生查自己的，教师查指导的，管理员查所有）
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        SysUser currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        boolean isStudentOwner = paperInfo.getStudentId().equals(currentUser.getId());
        boolean isAdmin = UserBusinessInfoUtils.isAdmin();
        if (!isStudentOwner && !isAdmin) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限查看该查重任务");
        }
        // 3. 转换为DTO（含完整报告信息）
        CheckTaskResultDTO resultDTO = convertToTaskResultDTO(checkTask);
        return Result.success("查重任务详情查询成功", resultDTO);
    }

    @Override
    public Result<CheckTaskResultDTO> getTeacherCheckTaskDetail(Long teacherId, Long taskId) {
        // 1. 按任务ID查询（教师端路径参数是 taskId，不能复用按 paperId 查询的学生端逻辑）
        CheckTask checkTask = getById(taskId);
        if (checkTask == null || Integer.valueOf(1).equals(checkTask.getIsDeleted())) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "查重任务不存在或已删除");
        }

        // 2. 权限校验：仅该论文的指导教师可查看
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        if (paperInfo == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }
        if (!Objects.equals(paperInfo.getTeacherId(), teacherId)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "仅指导教师可查看该查重任务");
        }

        // 3. 转换为DTO（含完整报告信息）
        return Result.success("查重任务详情查询成功", convertToTaskResultDTO(checkTask));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> cancelCheckTask(Long taskId) {
        // 1. 查询任务信息
        CheckTask checkTask = getById(taskId);
        if (checkTask == null || checkTask.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "查重任务不存在或已删除");
        }

        // 2. 状态校验（仅“待执行”状态可取消）
        if (!checkTask.getCheckStatus().equals(DictConstants.CheckStatus.PENDING)) {
            return Result.error(ResultCode.PERMISSION_NOT_STATUS, "仅“待执行”状态的任务可取消");
        }

        // 3. 权限校验
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        SysUser currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        boolean isStudentOwner = paperInfo.getStudentId().equals(currentUser.getId());
        boolean isAdmin = UserBusinessInfoUtils.isAdmin();
        if (!isStudentOwner && !isAdmin) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限取消该任务");
        }

        // 4. 条件抢占取消：PENDING -> CANCELLED。
        //    与异步消费者的 PENDING -> CHECKING 共用同一条件更新，二者只能成功一个；
        //    不再对查询出的实体做 updateById 全量回写。
        boolean cancelled = stateMachine.transitionStatus(
                taskId,
                CheckTaskStatusEnum.PENDING,
                CheckTaskStatusEnum.CANCELLED,
                "用户主动取消任务");
        if (!cancelled) {
            return Result.error(ResultCode.BUSINESS_NO_REPEAT, "任务已被执行或状态已变化，无法取消");
        }

       // 5. 恢复论文状态为"已分配"（论文已通过导师分配阶段，取消查重后回到待查重的已分配状态）
        PaperInfo updatePaper = new PaperInfo();
        updatePaper.setId(paperInfo.getId());
        paperStageGate.guard(paperInfo.getPaperStatus(), DictConstants.PaperStatus.ASSIGNED, "cancelCheckTask");
        updatePaper.setPaperStatus(DictConstants.PaperStatus.ASSIGNED); // 已分配，等待重新发起查重
        paperInfoMapper.updateById(updatePaper);

        // 6. 返还创建任务时扣减的学院月度额度。
        //    取消与失败同属「未消耗查重」，refundForFailedTask 内部以 quota_charged 1->0
        //    的原子翻转做幂等闸门，重复调用不会多退。
        collegeQuotaService.refundForFailedTask(taskId);

        return Result.success("查重任务取消成功");
    }

    @Override
    public CheckResultVO getCheckResult(Long paperId) {
        // 0. 数据归属校验（P0-3）：/api/check/result 只按 paperId 查询，
        //    必须确认论文属于当前登录学生，避免枚举 paperId 读取他人查重结论。
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        PaperInfo ownerPaper = paperInfoMapper.selectById(paperId);
        if (ownerPaper == null
                || Integer.valueOf(1).equals(ownerPaper.getIsDeleted())
                || !java.util.Objects.equals(ownerPaper.getStudentId(), currentUserId)) {
            log.warn("查重结果越权访问拦截 - paperId: {}, userId: {}", paperId, currentUserId);
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权查看该论文的查重结果");
        }

        // 1. Find the latest completed check task for the given paper
        LambdaQueryWrapper<CheckTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CheckTask::getPaperId, paperId)
                .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                .eq(CheckTask::getIsDeleted, 0)
                .orderByDesc(CheckTask::getCreateTime)
                .last("LIMIT 1");

        CheckTask checkTask = getOne(queryWrapper);

        if (checkTask == null) {
            return null; // No completed check task found
        }

        // 2. Convert CheckTask to CheckResultVO
        CheckResultVO resultVO = new CheckResultVO();
        resultVO.setTaskId(checkTask.getId());
        resultVO.setPaperId(checkTask.getPaperId());
        resultVO.setCheckStatus(checkTask.getCheckStatus());
        // M1.1 检测边界明示：查重结果响应声明检测范围
        resultVO.setCheckScope(CheckEngineManager.CHECK_SCOPE);
        resultVO.setCreateTime(checkTask.getCreateTime());

        // 3. Add report information if available
        if (checkTask.getReportId() != null) {
            CheckReport checkReport = checkReportMapper.selectById(checkTask.getReportId());
            if (checkReport != null) {
                resultVO.setReportId(checkReport.getId());
                resultVO.setReportNo(checkReport.getReportNo());
                resultVO.setReportPath(checkReport.getReportPath());
            }
        }

        return resultVO;
    }

    /**
     * M3.1：取该论文最新一次提交记录 ID（paper_submit.id）。
     * 无提交记录时返回 null（历史数据/未走提交流程的论文），不阻断任务创建。
     */
    private Long resolveLatestSubmitId(Long paperId) {
        PaperSubmit latest = paperSubmitMapper.selectOne(
                new LambdaQueryWrapper<PaperSubmit>()
                        .eq(PaperSubmit::getPaperId, paperId)
                        .orderByDesc(PaperSubmit::getSubmitVersion)
                        .last("LIMIT 1")
        );
        return latest != null ? latest.getId() : null;
    }

    /**
     * 生成唯一任务编号（格式：CHECK+ 年月日 +3 位序号，如 CHECK20251108001）
     */
    private String generateTaskNo() {
        String datePrefix = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seqPrefix = "CHECK" + datePrefix;
        // 查询当日最大序号
        LambdaQueryWrapper<CheckTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(CheckTask::getTaskNo, seqPrefix)
                .eq(CheckTask::getIsDeleted, 0)
                .select(CheckTask::getTaskNo)
                .orderByDesc(CheckTask::getTaskNo)
                .last("LIMIT 1");
        CheckTask lastTask = getOne(wrapper);

        int seq = 1;
        if (lastTask != null) {
            String lastNo = lastTask.getTaskNo();
            seq = Integer.parseInt(lastNo.substring(lastNo.length() - 3)) + 1;
        }
        // 序号补0（3位）
        return seqPrefix + String.format("%03d", seq);
    }

    /**
     * 批量转换任务 DTO（阶段 4 性能优化）。
     *
     * <p>先把本页所有 paperId / reportId 收集起来，各执行一次 selectBatchIds，
     * 转成 Map 后在循环内直接取值。N 条任务由 2N+1 次查询降为 3 次
     * （任务分页 1 次 + 论文批量 1 次 + 报告批量 1 次）。</p>
     */
    private List<CheckTaskResultDTO> convertToTaskResultDTOs(List<CheckTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> paperIds = tasks.stream()
                .map(CheckTask::getPaperId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, PaperInfo> paperMap = paperIds.isEmpty() ? Map.of()
                : paperInfoMapper.selectBatchIds(paperIds).stream()
                        .collect(Collectors.toMap(PaperInfo::getId, p -> p));

        List<Long> reportIds = tasks.stream()
                .filter(t -> DictConstants.CheckStatus.COMPLETED.equals(t.getCheckStatus()))
                .map(CheckTask::getReportId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, CheckReport> reportMap = reportIds.isEmpty() ? Map.of()
                : checkReportMapper.selectBatchIds(reportIds).stream()
                        .collect(Collectors.toMap(CheckReport::getId, r -> r));

        return tasks.stream()
                .map(task -> convertToTaskResultDTO(task,
                        paperMap.get(task.getPaperId()),
                        reportMap.get(task.getReportId())))
                .collect(Collectors.toList());
    }

    /**
     * 单任务转换（详情用）：复用批量装配，避免维护两套映射。
     */
    private CheckTaskResultDTO convertToTaskResultDTO(CheckTask checkTask) {
        List<CheckTaskResultDTO> dtos = convertToTaskResultDTOs(Collections.singletonList(checkTask));
        return dtos.isEmpty() ? null : dtos.get(0);
    }

    /**
     * 核心映射：只读取预载好的 paper/report，不再触发任何 DB 查询。
     */
    private CheckTaskResultDTO convertToTaskResultDTO(CheckTask checkTask, PaperInfo paperInfo,
                                                      CheckReport checkReport) {
        CheckTaskResultDTO dto = new CheckTaskResultDTO();
        dto.setTaskId(checkTask.getId());
        dto.setTaskNo(checkTask.getTaskNo());
        dto.setPaperId(checkTask.getPaperId());
        // M3.1 版本—报告绑定：任务上快照的版本与提交记录（存量任务为 null）
        dto.setPaperVersion(checkTask.getPaperVersion());
        dto.setSubmitId(checkTask.getSubmitId());
        dto.setCheckStatus(checkTask.getCheckStatus());
        // M1.1 检测边界明示：任务列表/详情声明检测范围
        dto.setCheckScope(CheckEngineManager.CHECK_SCOPE);
        dto.setCheckRate(checkTask.getCheckRate() != null ? checkTask.getCheckRate().doubleValue() : null);
        // P5 新指标：存量任务为 null，前端按「—」展示
        dto.setMaxPerSource(checkTask.getMaxPerSource() != null ? checkTask.getMaxPerSource().doubleValue() : null);
        dto.setCitationRate(checkTask.getCitationRate() != null ? checkTask.getCitationRate().doubleValue() : null);
        dto.setStartTime(checkTask.getStartTime());
        dto.setEndTime(checkTask.getEndTime());
        dto.setFailReason(checkTask.getFailReason());

        // 补充论文标题（预载 Map 取值）
        if (paperInfo != null) {
            dto.setPaperTitle(paperInfo.getPaperTitle());
        }

        // 补充报告摘要（仅任务成功且有报告时）
        if (DictConstants.CheckStatus.COMPLETED.equals(checkTask.getCheckStatus()) && checkReport != null) {
            CheckTaskResultDTO.CheckReportSummaryDTO reportSummary = new CheckTaskResultDTO.CheckReportSummaryDTO();
            reportSummary.setReportId(checkReport.getId());
            reportSummary.setReportNo(checkReport.getReportNo());
            // 解析重复段落数量（从JSON字符串中提取）
            List<Map<String, Object>> repeatDetails = JSON.parseObject(
                    checkReport.getRepeatDetails(), new TypeReference<List<Map<String, Object>>>() {});
            reportSummary.setRepeatParagraphCount(repeatDetails == null ? 0 : repeatDetails.size());
            // 构建报告下载URL（前端拼接域名，如：/api/student/reports/download?reportId=xxx）
            reportSummary.setReportDownloadUrl("/api/student/reports/download?reportId=" + checkReport.getId());
            dto.setReportSummary(reportSummary);
        }

        return dto;
    }

}
