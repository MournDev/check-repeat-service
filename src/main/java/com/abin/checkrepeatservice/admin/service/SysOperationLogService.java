package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.pojo.entity.SysOperationLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 操作日志服务接口
 */
public interface SysOperationLogService {

    /**
     * 保存操作日志
     * @param operationLog 操作日志对象
     */
    void saveOperationLog(SysOperationLog operationLog);

    /**
     * 分页查询操作日志
     * @param page 当前页
     * @param size 每页大小
     * @param operationType 操作类型
     * @param username 用户名
     * @param status 状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 分页结果
     */
    Page<SysOperationLog> getOperationLogPage(Integer page, Integer size,
                                              String operationType, String username,
                                              Integer status, LocalDateTime startTime,
                                              LocalDateTime endTime);

    /**
     * 获取操作统计信息
     * @param days 天数范围（默认7天）
     * @return 统计数据
     */
    Map<String, Object> getOperationStatistics(Integer days);

    /**
     * 获取热门操作统计
     * @param days 天数范围
     * @param limit 限制数量
     * @return 热门操作列表
     */
    List<Map<String, Object>> getHotOperations(Integer days, Integer limit);

    /**
     * 获取用户活跃度统计
     * @param days 天数范围
     * @return 用户活跃度数据
     */
    List<Map<String, Object>> getUserActivityStatistics(Integer days);

    /**
     * 获取模块使用统计
     * @param days 天数范围
     * @return 模块使用统计数据
     */
    List<Map<String, Object>> getModuleUsageStatistics(Integer days);

    /**
     * 根据ID获取操作日志详情
     * @param id 日志ID
     * @return 操作日志详情
     */
    SysOperationLog getOperationLogById(Long id);

    /**
     * 批量删除操作日志
     * @param ids 日志ID列表
     * @return 删除结果
     */
    boolean batchDeleteOperationLogs(List<Long> ids);

    /**
     * 清理过期操作日志
     * @param days 保留天数（默认30天）
     * @return 清理结果
     */
    boolean cleanExpiredLogs(Integer days);

    /**
     * 导出操作日志
     * @param operationType 操作类型
     * @param username 用户名
     * @param status 状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 导出的数据列表
     */
    List<SysOperationLog> exportOperationLogs(String operationType, String username,
                                              Integer status, LocalDateTime startTime,
                                              LocalDateTime endTime);
}