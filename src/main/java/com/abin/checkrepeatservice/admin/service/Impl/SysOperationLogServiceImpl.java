package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.SysOperationLogMapper;
import com.abin.checkrepeatservice.admin.service.SysOperationLogService;
import com.abin.checkrepeatservice.pojo.entity.SysOperationLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 操作日志服务实现类
 */
@Slf4j
@Service
public class SysOperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog> implements SysOperationLogService {

    @Resource
    private SysOperationLogMapper sysOperationLogMapper;

    @Override
    public void saveOperationLog(SysOperationLog operationLog) {
        try {
            // 设置操作时间
            if (operationLog.getOperationTime() == null) {
                operationLog.setOperationTime(LocalDateTime.now());
            }
            
            // 设置创建时间
            if (operationLog.getCreateTime() == null) {
                operationLog.setCreateTime(LocalDateTime.now());
            }
            
            // 保存到数据库
            boolean saved = this.save(operationLog);
            if (saved) {
                log.debug("操作日志保存成功: type={}, user={}, ip={}", 
                         operationLog.getOperationType(), 
                         operationLog.getUserName(), 
                         operationLog.getIpAddress());
            } else {
                log.warn("操作日志保存失败: type={}, user={}", 
                        operationLog.getOperationType(), 
                        operationLog.getUserName());
            }
        } catch (Exception e) {
            log.error("保存操作日志异常: type={}, user={}, error={}", 
                     operationLog.getOperationType(), 
                     operationLog.getUserName(), 
                     e.getMessage(), e);
        }
    }

    @Override
    public Page<SysOperationLog> getOperationLogPage(Integer page, Integer size,
                                                     String operationType, String username,
                                                     Integer status, LocalDateTime startTime,
                                                     LocalDateTime endTime) {
        try {
            Page<SysOperationLog> pageObj = new Page<>(page, size);
            return sysOperationLogMapper.selectOperationLogPage(pageObj, operationType, username, status, startTime, endTime);
        } catch (Exception e) {
            log.error("分页查询操作日志失败: {}", e.getMessage(), e);
            throw new RuntimeException("分页查询操作日志失败", e);
        }
    }

    @Override
    public Map<String, Object> getOperationStatistics(Integer days) {
        try {
            if (days == null || days <= 0) {
                days = 7; // 默认7天
            }
            return sysOperationLogMapper.getOperationStatistics(days);
        } catch (Exception e) {
            log.error("获取操作统计信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取操作统计信息失败", e);
        }
    }

    @Override
    public List<Map<String, Object>> getHotOperations(Integer days, Integer limit) {
        try {
            if (days == null || days <= 0) {
                days = 7;
            }
            if (limit == null || limit <= 0) {
                limit = 10; // 默认显示前10个
            }
            return sysOperationLogMapper.getHotOperations(days, limit);
        } catch (Exception e) {
            log.error("获取热门操作统计失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取热门操作统计失败", e);
        }
    }

    @Override
    public List<Map<String, Object>> getUserActivityStatistics(Integer days) {
        try {
            if (days == null || days <= 0) {
                days = 7;
            }
            return sysOperationLogMapper.getUserActivityStatistics(days);
        } catch (Exception e) {
            log.error("获取用户活跃度统计失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取用户活跃度统计失败", e);
        }
    }

    @Override
    public List<Map<String, Object>> getModuleUsageStatistics(Integer days) {
        try {
            if (days == null || days <= 0) {
                days = 7;
            }
            return sysOperationLogMapper.getModuleUsageStatistics(days);
        } catch (Exception e) {
            log.error("获取模块使用统计失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取模块使用统计失败", e);
        }
    }

    @Override
    public SysOperationLog getOperationLogById(Long id) {
        try {
            return this.getById(id);
        } catch (Exception e) {
            log.error("根据ID获取操作日志失败: id={}, error={}", id, e.getMessage(), e);
            throw new RuntimeException("获取操作日志详情失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteOperationLogs(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return false;
            }
            
            int deleted = sysOperationLogMapper.batchDeleteOperationLogs(ids);
            log.info("批量删除操作日志完成: ids={}, deleted={}", ids, deleted);
            return deleted > 0;
        } catch (Exception e) {
            log.error("批量删除操作日志失败: ids={}, error={}", ids, e.getMessage(), e);
            throw new RuntimeException("批量删除操作日志失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cleanExpiredLogs(Integer days) {
        try {
            if (days == null || days <= 0) {
                days = 30; // 默认保留30天
            }
            
            int cleaned = sysOperationLogMapper.cleanExpiredLogs(days);
            log.info("清理过期操作日志完成: days={}, cleaned={}", days, cleaned);
            return true;
        } catch (Exception e) {
            log.error("清理过期操作日志失败: days={}, error={}", days, e.getMessage(), e);
            throw new RuntimeException("清理过期操作日志失败", e);
        }
    }

    @Override
    public List<SysOperationLog> exportOperationLogs(String operationType, String username,
                                                     Integer status, LocalDateTime startTime,
                                                     LocalDateTime endTime) {
        try {
            // 使用大分页获取所有数据
            Page<SysOperationLog> page = new Page<>(1, 10000); // 最多导出1万条
            Page<SysOperationLog> result = sysOperationLogMapper.selectOperationLogPage(
                page, operationType, username, status, startTime, endTime);
            return result.getRecords();
        } catch (Exception e) {
            log.error("导出操作日志失败: {}", e.getMessage(), e);
            throw new RuntimeException("导出操作日志失败", e);
        }
    }
}