package com.abin.checkrepeatservice.teacher.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 教师控制台Mapper接口
 * 提供复杂的统计查询SQL
 */
@Mapper
public interface TeacherDashboardMapper {

    /**
     * 统计指导学生总数
     */
    Long countTotalStudents(@Param("teacherId") Long teacherId);

    /**
     * 统计待审核论文数量
     */
    Long countPendingPapers(@Param("teacherId") Long teacherId);

    /**
     * 统计已审核论文数量
     */
    Long countReviewedPapers(@Param("teacherId") Long teacherId);

    /**
     * 统计审核通过的论文数量
     */
    Long countPassedPapers(@Param("teacherId") Long teacherId);

    /**
     * 统计已提交论文数
     */
    Long countSubmittedPapers(@Param("teacherId") Long teacherId);

    /**
     * 统计审核中论文数
     */
    Long countAuditingPapers(@Param("teacherId") Long teacherId);

    /**
     * 统计被驳回的论文数
     */
    Long countRejectedPapers(@Param("teacherId") Long teacherId);

    /**
     * 获取论文状态分布统计
     */
    @MapKey("status")  // 指定使用status字段作为Map的键
    List<Map<String, Object>> getPaperStatusDistribution(@Param("teacherId") Long teacherId);

    /**
     * 获取各专业审核情况统计
     */
    @MapKey("majorId")  // 指定使用majorId字段作为Map的键
    List<Map<String, Object>> getMajorReviewStatistics(@Param("teacherId") Long teacherId);

    /**
     * 获取时间趋势统计（近30天）
     */
    @MapKey("date")  // 指定使用date字段作为Map的键
    List<Map<String, Object>> getTimeTrendStatistics(@Param("teacherId") Long teacherId);

    /**
     * 获取近期审核活动记录
     */
    @MapKey("activityId")  // 指定使用activityId字段作为Map的键
    List<Map<String, Object>> getRecentReviewActivities(@Param("teacherId") Long teacherId, @Param("page") Page<?> page);

    /**
     * 根据教师ID获取学生IDs
     */
    List<Long> getStudentIdsByTeacher(@Param("teacherId") Long teacherId);

    /**
     * 统计新提交论文数（指定小时内）
     */
    Long countNewSubmissions(@Param("teacherId") Long teacherId, @Param("hours") int hours);

    /**
     * 统计不活跃学生数（指定天内未提交）
     */
    Long countInactiveStudents(@Param("teacherId") Long teacherId, @Param("days") int days);

    /**
     * 统计今日审核数
     */
    Long countTodayReviews(@Param("teacherId") Long teacherId);

    /**
     * 统计今日通过数
     */
    Long countTodayPasses(@Param("teacherId") Long teacherId);

    /**
     * 计算平均审核耗时（小时）
     */
    Double getAverageReviewTime(@Param("teacherId") Long teacherId);
}