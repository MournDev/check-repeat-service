package com.abin.checkrepeatservice.common.statemachine;

import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 查重任务状态机管理器
 * 负责安全的状态流转控制和并发处理
 */
@Component
@Slf4j
public class CheckTaskStateMachine {
    
    @Autowired
    private CheckTaskMapper checkTaskMapper;
    
    @Autowired(required = false)
    private RedissonClient redissonClient;
    
    private static final String LOCK_PREFIX = "check_task_lock:";
    private static final long LOCK_WAIT_TIME = 3000; // 3秒等待锁
    private static final long LOCK_LEASE_TIME = 10000; // 10秒锁过期
    
    /**
     * 安全的状态转换
     * @param taskId 任务ID
     * @param targetStatus 目标状态
     * @param reason 转换原因
     * @return 是否转换成功
     */
    public boolean transitionStatus(Long taskId, CheckTaskStatusEnum targetStatus, String reason) {
        String lockKey = LOCK_PREFIX + taskId;
        RLock lock = redissonClient != null ? redissonClient.getLock(lockKey) : null;
        
        try {
            // 尝试获取分布式锁
            boolean locked = lock == null || lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.MILLISECONDS);
            if (lock != null && !locked) {
                log.warn("获取任务锁超时，taskId={}", taskId);
                return false;
            }
            
            // 查询当前任务状态
            CheckTask task = checkTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("任务不存在，taskId={}", taskId);
                return false;
            }
            
            CheckTaskStatusEnum currentStatus = CheckTaskStatusEnum.fromCode(task.getCheckStatus());
            
            // 验证状态转换合法性
            if (!currentStatus.canTransitionTo(targetStatus)) {
                log.warn("非法状态转换：{} -> {}，taskId={}", 
                        currentStatus.getDescription(), targetStatus.getDescription(), taskId);
                return false;
            }
            
            // 执行状态转换
            task.setCheckStatus(targetStatus.getCode());
            
            // 设置相关时间字段
            if (targetStatus == CheckTaskStatusEnum.CHECKING) {
                task.setStartTime(LocalDateTime.now());
            } else if (targetStatus.isFinalStatus()) {
                task.setEndTime(LocalDateTime.now());
                if (task.getStartTime() != null) {
                    long duration = java.time.Duration.between(task.getStartTime(), task.getEndTime()).getSeconds();
                    task.setDurationSeconds(duration);
                }
            }
            
            // 更新失败原因（如果是失败状态）
            if (targetStatus == CheckTaskStatusEnum.FAILURE && reason != null) {
                task.setFailReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
            }
            
            // 保存更新
            checkTaskMapper.updateById(task);
            
            log.info("状态转换成功：{} -> {}，taskId={}，reason={}", 
                    currentStatus.getDescription(), targetStatus.getDescription(), taskId, reason);
            
            return true;
            
        } catch (Exception e) {
            log.error("状态转换失败，taskId={}", taskId, e);
            return false;
        } finally {
            // 释放锁
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
    /**
     * 批量状态转换（用于定时任务清理等场景）
     */
    public int batchTransitionStatus(CheckTaskStatusEnum targetStatus, String reason, String conditionSql) {
        try {
            // 直接使用LambdaUpdateWrapper
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CheckTask> updateWrapper = 
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CheckTask>()
                    .set(CheckTask::getCheckStatus, targetStatus.getCode())
                    .set(CheckTask::getEndTime, LocalDateTime.now())
                    .set(CheckTask::getFailReason, reason != null ? reason : "")
                    .apply(conditionSql);
            
            int affectedRows = checkTaskMapper.update(null, updateWrapper);
            
            log.info("批量状态转换完成：目标状态={}，影响行数={}，条件={}", 
                    targetStatus.getDescription(), affectedRows, conditionSql);
            
            return affectedRows;
            
        } catch (Exception e) {
            log.error("批量状态转换失败", e);
            return 0;
        }
    }
    
    /**
     * 验证状态转换是否合法（不执行转换）
     * @param currentStatus 当前状态
     * @param targetStatus 目标状态
     * @return 是否合法
     */
    public boolean validateTransition(CheckTaskStatusEnum currentStatus, CheckTaskStatusEnum targetStatus) {
        return currentStatus.canTransitionTo(targetStatus);
    }
    
    /**
     * 获取任务当前状态
     * @param taskId 任务ID
     * @return 当前状态枚举
     */
    public CheckTaskStatusEnum getCurrentStatus(Long taskId) {
        CheckTask task = checkTaskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }
        return CheckTaskStatusEnum.fromCode(task.getCheckStatus());
    }
    
    /**
     * 检查任务是否处于某个状态
     * @param taskId 任务ID
     * @param status 目标状态
     * @return 是否匹配
     */
    public boolean isInStatus(Long taskId, CheckTaskStatusEnum status) {
        CheckTaskStatusEnum currentStatus = getCurrentStatus(taskId);
        return currentStatus == status;
    }
    
    /**
     * 获取任务执行耗时（秒）
     * @param taskId 任务ID
     * @return 耗时秒数，未完成返回-1
     */
    public long getTaskDuration(Long taskId) {
        CheckTask task = checkTaskMapper.selectById(taskId);
        if (task == null || task.getStartTime() == null || task.getEndTime() == null) {
            return -1;
        }
        return java.time.Duration.between(task.getStartTime(), task.getEndTime()).getSeconds();
    }
}