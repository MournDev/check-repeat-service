package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.admin.service.CollegeQuotaService;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperSubmit;
import com.abin.checkrepeatservice.student.dto.BatchCheckRequestDTO;
import com.abin.checkrepeatservice.student.dto.BatchCheckResultDTO;
import com.abin.checkrepeatservice.student.event.CheckTaskCreatedEvent;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.service.CheckTaskValidationService;
import com.abin.checkrepeatservice.student.service.CheckTaskService;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 批量查重服务实现
 */
@Service
@Slf4j
public class BatchCheckTaskServiceImpl {

    @Resource
    private CheckTaskValidationService validationService;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private CheckTaskService checkTaskService;

    // P5 学院月度查重额度：批量入口逐篇新建任务前校验并扣减
    @Resource
    private CollegeQuotaService collegeQuotaService;

    // M3.1 版本—报告绑定：任务落库时快照论文版本与最新提交记录
    @Resource
    private com.abin.checkrepeatservice.admin.mapper.PaperSubmitMapper paperSubmitMapper;

    /**
     * S13：批量创建并行化用线程池（AsyncConfig 的通用池，core 3 / max 10 / CallerRunsPolicy 兜底）。
     * 单篇处理只做校验+建库+发事件（查重执行本就走 @Async 监听器），
     * 额度扣减为原子 SQL（行锁），并行安全。
     */
    @Resource(name = "asyncExecutor")
    private ThreadPoolTaskExecutor asyncExecutor;

    /**
     * 批量创建查重任务
     * 
     * @param request 批量请求参数
     * @return 批量结果
     */
    public Result<BatchCheckResultDTO> batchCreateCheckTasks(BatchCheckRequestDTO request) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        List<Long> paperIds = request.getPaperIds();

        if (paperIds == null || paperIds.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "论文 ID 列表不能为空");
        }

        // 限制批量数量
        if (paperIds.size() > 20) {
            return Result.error(ResultCode.PARAM_ERROR, "单次批量查重最多支持 20 篇论文");
        }

        List<BatchCheckResultDTO.TaskResult> successList = new ArrayList<>();
        List<BatchCheckResultDTO.TaskResult> failedList = new ArrayList<>();

        // S13：逐篇串行改并行——原循环每篇串行执行多次 DB 校验/建库（最多 20 篇），
        // HTTP 请求被累计阻塞；改为 CompletableFuture 并行处理，join 后按入参顺序汇总，
        // 单篇失败仍只计入 failedList，不影响其他论文
        List<CompletableFuture<BatchCheckResultDTO.TaskResult>> futures = paperIds.stream()
            .map(paperId -> CompletableFuture.supplyAsync(
                () -> processSinglePaperSafely(paperId, currentUserId), asyncExecutor))
            .toList();

        for (CompletableFuture<BatchCheckResultDTO.TaskResult> future : futures) {
            BatchCheckResultDTO.TaskResult result = future.join();
            if (result.getSuccess()) {
                successList.add(result);
            } else {
                failedList.add(result);
            }
        }

        // 构建返回结果
        BatchCheckResultDTO batchResult = BatchCheckResultDTO.builder()
            .totalCount(paperIds.size())
            .successCount(successList.size())
            .failedCount(failedList.size())
            .successList(successList)
            .failedList(failedList)
            .estimatedTotalTime(estimateTotalBatchTime(successList))
            .build();

        return Result.success("批量查重任务创建完成", batchResult);
    }

    /**
     * 单篇处理兜底：任何未预期异常都按单篇失败返回，不让 CompletableFuture 携带异常中断汇总
     */
    private BatchCheckResultDTO.TaskResult processSinglePaperSafely(Long paperId, Long currentUserId) {
        try {
            return processSinglePaper(paperId, currentUserId);
        } catch (Exception e) {
            log.error("批量查重处理失败 - 论文 ID: {}", paperId, e);
            return BatchCheckResultDTO.TaskResult.builder()
                .paperId(paperId)
                .success(false)
                .message("系统异常：" + e.getMessage())
                .build();
        }
    }

    /**
     * 处理单篇论文
     */
    private BatchCheckResultDTO.TaskResult processSinglePaper(Long paperId, Long studentId) {
        // 1. 校验
        CheckTaskValidationService.ValidationResult validationResult = 
            validationService.validateCheckRequest(paperId, studentId);

        if (!validationResult.isSuccess()) {
            return BatchCheckResultDTO.TaskResult.builder()
                .paperId(paperId)
                .success(false)
                .message(validationResult.getMessage())
                .build();
        }

        PaperInfo paperInfo = validationResult.getPaper();

        // P5 学院月度额度：新建任务落库前校验并扣减学院当月额度。
        // 重试路径复用已存在任务，不经过本批量新建入口，不会重复扣减。
        boolean quotaConsumed;
        try {
            // M4.1：记录本次是否实际扣减，失败返还时据此幂等退回
            quotaConsumed = collegeQuotaService.checkAndConsume(paperInfo.getCollegeId());
        } catch (BusinessException e) {
            // 额度不足（40916）按单篇失败返回，不中断批量中其他论文
            return BatchCheckResultDTO.TaskResult.builder()
                .paperId(paperId)
                .success(false)
                .message(e.getMessage())
                .build();
        }

        // 2. 创建任务
        try {
            CheckTask checkTask = createCheckTask(paperId, paperInfo, studentId, quotaConsumed);
            
            // 3. 发布事件（异步执行）
            eventPublisher.publishEvent(new CheckTaskCreatedEvent(this, checkTask.getId(), paperId));

            // 4. 计算预估时间
            int estimatedTime = estimateDuration(paperInfo.getWordCount());
            int queuePosition = getQueuePosition();
            int waitTime = estimateWaitTime(queuePosition);

            return BatchCheckResultDTO.TaskResult.builder()
                .paperId(paperId)
                .taskId(checkTask.getId())
                .success(true)
                .message("查重任务创建成功")
                .estimatedTime(estimatedTime)
                .queuePosition(queuePosition)
                .waitTime(waitTime)
                .build();
        } catch (Exception e) {
            log.error("创建查重任务失败 - 论文 ID: {}", paperId, e);
            return BatchCheckResultDTO.TaskResult.builder()
                .paperId(paperId)
                .success(false)
                .message("创建失败：" + e.getMessage())
                .build();
        }
    }

    /**
     * 创建查重任务
     */
    @Transactional(rollbackFor = Exception.class)
    protected CheckTask createCheckTask(Long paperId, PaperInfo paperInfo, Long studentId,
                                        boolean quotaConsumed) {
        CheckTask checkTask = new CheckTask();
        checkTask.setPaperId(paperId);
        checkTask.setFileId(paperInfo.getFileId());
        checkTask.setTaskNo(generateTaskNo());
        checkTask.setCheckStatus("PENDING");
        // M4.1：记录本次是否实际扣减学院月度额度，失败返还时据此幂等退回
        checkTask.setQuotaCharged(quotaConsumed ? 1 : 0);
        // M3.1 版本—报告绑定：快照论文版本 + 最新一次提交记录
        checkTask.setPaperVersion(paperInfo.getVersion());
        checkTask.setSubmitId(resolveLatestSubmitId(paperId));
        // S13：本方法在并行线程中执行，SecurityContext 取不到当前用户
        // （MyMetaObjectHandler 会兜底填匿名管理员 ID），审计字段显式写入；
        // MP strictInsertFill 不覆盖非空字段，不会被自动填充回退
        LocalDateTime now = LocalDateTime.now();
        checkTask.setCreateBy(studentId);
        checkTask.setCreateTime(now);
        checkTask.setUpdateBy(studentId);
        checkTask.setUpdateTime(now);

        // 保存任务
        checkTaskMapper.insert(checkTask);
        
        return checkTask;
    }

    /**
     * M3.1：取该论文最新一次提交记录 ID（paper_submit.id）。
     * 无提交记录时返回 null（历史数据/未走提交流程的论文），不阻断任务创建。
     */
    private Long resolveLatestSubmitId(Long paperId) {
        PaperSubmit latest = paperSubmitMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaperSubmit>()
                        .eq(PaperSubmit::getPaperId, paperId)
                        .orderByDesc(PaperSubmit::getSubmitVersion)
                        .last("LIMIT 1")
        );
        return latest != null ? latest.getId() : null;
    }

    /**
     * 生成任务编号
     */
    private String generateTaskNo() {
        return "CT" + System.currentTimeMillis() + (int)(Math.random() * 1000);
    }

    /**
     * 获取当前排队位置
     */
    private int getQueuePosition() {
        // 查询 PENDING 状态的任务数 +1
        long pendingCount = ((CheckTaskServiceImpl)checkTaskService).count(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.abin.checkrepeatservice.pojo.entity.CheckTask>()
                .eq(com.abin.checkrepeatservice.pojo.entity.CheckTask::getCheckStatus, "PENDING")
                .eq(com.abin.checkrepeatservice.pojo.entity.CheckTask::getIsDeleted, 0)
        );
        return (int)pendingCount + 1;
    }

    /**
     * 预估等待时间（秒）
     */
    private int estimateWaitTime(int queuePosition) {
        // 假设每个任务平均需要 60 秒
        return queuePosition * 60;
    }

    /**
     * 预估总时长（批量）
     */
    private int estimateTotalBatchTime(List<BatchCheckResultDTO.TaskResult> successList) {
        if (successList.isEmpty()) {
            return 0;
        }
        
        // 并行执行，取最大值
        return successList.stream()
            .mapToInt(BatchCheckResultDTO.TaskResult::getEstimatedTime)
            .max()
            .orElse(0);
    }

    /**
     * 优化的时间预估算法
     * 
     * @param wordCount 字数
     * @return 预估秒数
     */
    private int estimateDuration(Integer wordCount) {
        if (wordCount == null || wordCount == 0) {
            return 60; // 默认 60 秒
        }

        // 分段预估算法
        double baseTime;
        if (wordCount <= 5000) {
            // 5000 字以下：每 1000 字 5 秒
            baseTime = wordCount / 1000.0 * 5;
        } else if (wordCount <= 10000) {
            // 5000-10000 字：基础 25 秒 + 超出部分每 1000 字 8 秒
            baseTime = 25 + (wordCount - 5000) / 1000.0 * 8;
        } else if (wordCount <= 20000) {
            // 10000-20000 字：基础 65 秒 + 超出部分每 1000 字 10 秒
            baseTime = 65 + (wordCount - 10000) / 1000.0 * 10;
        } else {
            // 20000 字以上：基础 165 秒 + 超出部分每 1000 字 12 秒
            baseTime = 165 + (wordCount - 20000) / 1000.0 * 12;
        }

        // 考虑系统负载系数（当前并发数/最大并发数）
        double loadFactor = getCurrentLoadFactor();
        baseTime = baseTime * (1 + loadFactor);

        // 向上取整，最少 30 秒
        return Math.max(30, (int) Math.ceil(baseTime));
    }

    /**
     * 获取当前系统负载系数
     * 
     * @return 负载系数 (0.0-1.0)
     */
    private double getCurrentLoadFactor() {
        // 基于当前并发任务数计算负载系数
        try {
            // 获取正在执行的任务数
            long runningCount = ((CheckTaskServiceImpl)checkTaskService).count(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.abin.checkrepeatservice.pojo.entity.CheckTask>()
                    .eq(com.abin.checkrepeatservice.pojo.entity.CheckTask::getCheckStatus, "CHECKING")
                    .eq(com.abin.checkrepeatservice.pojo.entity.CheckTask::getIsDeleted, 0)
            );
            
            // 假设最大并发为 10
            int maxConcurrent = 10;
            double loadFactor = Math.min((double)runningCount / maxConcurrent, 1.0);
            
            log.debug("当前系统负载：{}/{}={}, 负载系数：{}", 
                runningCount, maxConcurrent, runningCount, loadFactor);
            
            return loadFactor;
        } catch (Exception e) {
            log.error("获取系统负载失败", e);
            return 0.2; // 默认负载系数
        }
    }
}
