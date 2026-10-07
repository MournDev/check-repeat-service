package com.abin.checkrepeatservice.schedule;

import com.abin.checkrepeatservice.admin.mapper.CheckResultMapper;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.CheckResult;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据完整性校验定时任务
 * 用于检查和修复查重相关数据的一致性问题
 */
@Component
@Slf4j
public class DataIntegrityChecker {

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private CheckResultMapper checkResultMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    /**
     * 数据完整性检查开关，默认关。
     *
     * <p>本任务在加上 {@code @EnableScheduling} 之前从未执行过，
     * 而它会插查重结果、并改写论文的重复率与完成标记（见下方各 fix* 方法），
     * 属"会按历史数据自动修库"的一类。上线不应是它第一次跑，
     * 需要显式打开 {@code schedule.data-integrity.enabled} 并先观察一遍输出。</p>
     */
    @Value("${schedule.data-integrity.enabled:false}")
    private boolean integrityCheckEnabled;

    /**
     * 每天凌晨 3 点执行数据完整性检查
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void checkDataIntegrity() {
        if (!integrityCheckEnabled) {
            log.debug("数据完整性检查已关闭（schedule.data-integrity.enabled=false），跳过");
            return;
        }
        log.info("开始执行数据完整性检查任务");
        
        int fixedCount = 0;
        
        try {
            // 1. 检查已完成的查重任务是否有对应的查重结果
            fixedCount += fixMissingCheckResults();
            
            // 2. 检查查重结果与论文信息的一致性
            fixedCount += fixInconsistentPaperInfo();
            
            // 3. 检查未设置查重完成标记的记录
            fixedCount += fixMissingCheckCompletedFlag();
            
            // 4. 检查长期处于查重中的任务（失败检测）
            fixedCount += checkFailedCheckTasks();
            
            log.info("数据完整性检查完成，共修复{}条记录", fixedCount);
            
        } catch (Exception e) {
            log.error("数据完整性检查失败", e);
        }
    }

    /**
     * 修复缺失的查重结果记录
     * 场景：check_task 状态为 completed，但 check_result 表无对应记录
     */
    private int fixMissingCheckResults() {
        log.info("检查缺失的查重结果记录...");
        
        // 查询所有已完成但没有查重结果的任務
        List<CheckTask> tasksWithoutResults = checkTaskMapper.selectList(
            new LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getCheckStatus, "completed")
                .isNotNull(CheckTask::getReportId)
                .orderByDesc(CheckTask::getEndTime)
        ).stream().filter(task -> {
            // 在内存中过滤出没有对应 check_result 的任务
            Long count = checkResultMapper.selectCount(
                new LambdaQueryWrapper<com.abin.checkrepeatservice.pojo.entity.CheckResult>()
                    .eq(com.abin.checkrepeatservice.pojo.entity.CheckResult::getTaskId, task.getId())
            );
            return count == 0;
        }).collect(Collectors.toList());
        
        if (tasksWithoutResults.isEmpty()) {
            log.info("未发现缺失的查重结果记录");
            return 0;
        }
        
        log.info("发现{}条缺失查重结果的任务", tasksWithoutResults.size());
        
        int fixedCount = 0;
        for (CheckTask task : tasksWithoutResults) {
            try {
                // 从 check_task 中提取信息创建 check_result
                com.abin.checkrepeatservice.pojo.entity.CheckResult checkResult = 
                    new com.abin.checkrepeatservice.pojo.entity.CheckResult();
                checkResult.setTaskId(task.getId());
                checkResult.setPaperId(task.getPaperId());
                checkResult.setRepeatRate(task.getCheckRate());
                checkResult.setCheckSource("LOCAL");
                checkResult.setCheckTime(task.getEndTime());
                checkResult.setStatus(1);
                
                UserBusinessInfoUtils.setAuditField(checkResult, true);
                checkResultMapper.insert(checkResult);
                
                fixedCount++;
                log.info("修复缺失的查重结果 - 任务 ID: {}", task.getId());
                
            } catch (Exception e) {
                log.error("修复缺失的查重结果失败 - 任务 ID: {}", task.getId(), e);
            }
        }
        
        return fixedCount;
    }

    /**
     * 修复论文信息与查重结果不一致的问题
     * 场景：paper_info.similarity_rate 与最新 check_result.repeat_rate 不一致
     */
    private int fixInconsistentPaperInfo() {
        log.info("检查论文信息与查重结果的一致性...");
        
        // 查询所有已完成查重的论文
        List<PaperInfo> papers = paperInfoMapper.selectList(
            new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getCheckCompleted, 1)
                .isNotNull(PaperInfo::getSimilarityRate)
        );
        
        int fixedCount = 0;
        for (PaperInfo paper : papers) {
            try {
                // 获取该论文最新的查重结果
                List<CheckResult> results = checkResultMapper.selectList(
                    new LambdaQueryWrapper<CheckResult>()
                        .eq(CheckResult::getPaperId, paper.getId())
                        .eq(CheckResult::getStatus, 1)
                        .orderByDesc(CheckResult::getCreateTime)
                );
                
                if (results.isEmpty()) {
                    continue;
                }
                
                CheckResult latestResult = results.get(0);
                
                // 比较相似度是否一致
                if (paper.getSimilarityRate().compareTo(latestResult.getRepeatRate()) != 0) {
                    // 更新论文信息的相似度
                    PaperInfo updatePaper = new PaperInfo();
                    updatePaper.setId(paper.getId());
                    updatePaper.setSimilarityRate(latestResult.getRepeatRate());
                    updatePaper.setUpdateTime(LocalDateTime.now());
                    paperInfoMapper.updateById(updatePaper);
                    
                    fixedCount++;
                    log.info("修复论文相似度不一致 - 论文 ID: {}, 原值：{}, 新值：{}", 
                            paper.getId(), paper.getSimilarityRate(), latestResult.getRepeatRate());
                }
                
            } catch (Exception e) {
                log.error("修复论文信息不一致失败 - 论文 ID: {}", paper.getId(), e);
            }
        }
        
        return fixedCount;
    }

    /**
     * 修复未设置查重完成标记的记录
     * 场景：有完成的查重任务，但 paper_info.check_completed 为 null 或 0
     */
    private int fixMissingCheckCompletedFlag() {
        log.info("检查缺失的查重完成标记...");
        
        // 查询所有有已完成查重任务但标记未设置的论文
        List<PaperInfo> papers = paperInfoMapper.selectList(
            new LambdaQueryWrapper<PaperInfo>()
                .and(wrapper -> wrapper
                    .isNull(PaperInfo::getCheckCompleted)
                    .or()
                    .eq(PaperInfo::getCheckCompleted, 0)
                )
        );
        
        int fixedCount = 0;
        for (PaperInfo paper : papers) {
            try {
                // 检查是否存在已完成的查重任务
                Long completedTaskCount = checkTaskMapper.selectCount(
                    new LambdaQueryWrapper<CheckTask>()
                        .eq(CheckTask::getPaperId, paper.getId())
                        .eq(CheckTask::getCheckStatus, "completed")
                );
                
                if (completedTaskCount > 0) {
                    // 更新查重完成标记
                    PaperInfo updatePaper = new PaperInfo();
                    updatePaper.setId(paper.getId());
                    updatePaper.setCheckCompleted(1);
                    updatePaper.setUpdateTime(LocalDateTime.now());
                    paperInfoMapper.updateById(updatePaper);
                    
                    fixedCount++;
                    log.info("修复缺失的查重完成标记 - 论文 ID: {}", paper.getId());
                }
                
            } catch (Exception e) {
                log.error("修复查重完成标记失败 - 论文 ID: {}", paper.getId(), e);
            }
        }
        
        return fixedCount;
    }

    /**
     * 检查长期处于查重中的任务（可能失败）
     * 场景：check_task 状态为 checking 超过 30 分钟
     */
    private int checkFailedCheckTasks() {
        log.info("检查长期处于查重中的任务...");
        
        // 查询所有正在进行超过 30 分钟的查重任务
        LocalDateTime thresholdTime = LocalDateTime.now().minusMinutes(30);
        List<CheckTask> stuckTasks = checkTaskMapper.selectList(
            new LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getCheckStatus, "checking")
                .lt(CheckTask::getStartTime, thresholdTime)
                .orderByAsc(CheckTask::getStartTime)
        );
        
        if (stuckTasks.isEmpty()) {
            log.info("未发现长期处于查重中的任务");
            return 0;
        }
        
        log.warn("发现{}条长期处于查重中的任务，将标记为失败", stuckTasks.size());
        
        int fixedCount = 0;
        for (CheckTask task : stuckTasks) {
            try {
                // 更新任务状态为失败
                CheckTask updateTask = new CheckTask();
                updateTask.setId(task.getId());
                updateTask.setCheckStatus("failed");
                updateTask.setEndTime(LocalDateTime.now());
                updateTask.setFailReason("查重超时，自动标记为失败");
                updateTask.setUpdateTime(LocalDateTime.now());
                checkTaskMapper.updateById(updateTask);
                
                // 更新论文状态
                PaperInfo updatePaper = new PaperInfo();
                updatePaper.setId(task.getPaperId());
                updatePaper.setCheckCompleted(0);
                updatePaper.setCheckResult("查重失败：系统超时");
                updatePaper.setPaperStatus(DictConstants.PaperStatus.PENDING); // 重置为待分配状态
                updatePaper.setUpdateTime(LocalDateTime.now());
                paperInfoMapper.updateById(updatePaper);
                
                fixedCount++;
                log.warn("标记失败的查重任务 - 任务 ID: {}, 论文 ID: {}", task.getId(), task.getPaperId());
                
            } catch (Exception e) {
                log.error("标记失败任务失败 - 任务 ID: {}", task.getId(), e);
            }
        }
        
        return fixedCount;
    }
}
