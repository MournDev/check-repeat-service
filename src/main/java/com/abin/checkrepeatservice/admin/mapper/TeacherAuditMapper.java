package com.abin.checkrepeatservice.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.Map;

/**
 * 教师审核 Mapper 接口：定义审核相关的数据库操作
 */
@Mapper
public interface TeacherAuditMapper {

    /**
     * 统计教师的待审核任务数
     * @param teacherId 教师ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 待审核任务数
     */
    int countPendingAudit(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 统计教师的已审核任务数与总审核耗时（秒）
     * @param teacherId 教师ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return key：count（已审核数），total_time（总耗时秒）
     */
    Map<String, Object> countCompletedAudit(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

}