package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.pojo.entity.SysOperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 操作日志Mapper接口
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {

    /**
     * 分页查询操作日志
     * @param page 分页对象
     * @param operationType 操作类型（可选）
     * @param username 用户名（可选）
     * @param status 状态（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选，开区间上界，配合 < 比较）
     * @param userType 用户类型（可选）
     * @param keyword 关键字，匹配用户名或操作描述（可选）
     * @return 分页结果
     */
    Page<SysOperationLog> selectOperationLogPage(Page<SysOperationLog> page,
                                                 @Param("operationType") String operationType,
                                                 @Param("username") String username,
                                                 @Param("status") Integer status,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime,
                                                 @Param("userType") String userType,
                                                 @Param("keyword") String keyword);

    /**
     * 获取操作统计信息
     * @param days 天数范围
     * @return 统计数据
     */
    Map<String, Object> getOperationStatistics(@Param("days") Integer days);

    /**
     * 获取热门操作统计
     * @param days 天数范围
     * @param limit 限制数量
     * @return 热门操作列表
     */
    List<Map<String, Object>> getHotOperations(@Param("days") Integer days, @Param("limit") Integer limit);

    /**
     * 获取用户活跃度统计
     * @param days 天数范围
     * @return 用户活跃度数据
     */
    List<Map<String, Object>> getUserActivityStatistics(@Param("days") Integer days);

    /**
     * 获取模块使用统计
     * @param days 天数范围
     * @return 模块使用统计数据
     */
    List<Map<String, Object>> getModuleUsageStatistics(@Param("days") Integer days);

    /**
     * 批量删除操作日志
     * @param ids 日志ID列表
     * @return 删除条数
     */
    int batchDeleteOperationLogs(@Param("ids") List<Long> ids);

    /**
     * 清理过期操作日志
     * @param days 保留天数
     * @return 清理条数
     */
    int cleanExpiredLogs(@Param("days") Integer days);

    /**
     * 审计日志汇总（P5 超管端）：按操作类型分组计数
     * @param startTime 开始时间（含，可空=不限）
     * @param endTime   结束时间（不含，可空=不限）
     * @return [{key, count}]，key 为 operation_type
     */
    List<Map<String, Object>> summarizeByOperationType(@Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 审计日志汇总（P5 超管端）：按用户类型分组计数
     * @param startTime 开始时间（含，可空=不限）
     * @param endTime   结束时间（不含，可空=不限）
     * @return [{key, count}]，key 为 user_type
     */
    List<Map<String, Object>> summarizeByUserType(@Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);
}