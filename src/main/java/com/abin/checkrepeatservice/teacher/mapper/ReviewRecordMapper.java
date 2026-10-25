package com.abin.checkrepeatservice.teacher.mapper;

import com.abin.checkrepeatservice.pojo.entity.ReviewRecord;
import com.abin.checkrepeatservice.teacher.vo.ReviewStatusVO;
import com.abin.checkrepeatservice.teacher.vo.TrendComparisonVO;
import com.abin.checkrepeatservice.teacher.vo.TrendDataVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewRecordMapper extends BaseMapper<ReviewRecord> {

    /**
     * 按天统计审核趋势
     */
    List<TrendDataVO> selectDailyTrend(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 按周统计审核趋势
     */
    List<TrendDataVO> selectWeeklyTrend(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 按月统计审核趋势
     */
    List<TrendDataVO> selectMonthlyTrend(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 获取审核统计数据对比
     */
    TrendComparisonVO selectTrendComparison(
            @Param("teacherId") Long teacherId,
            @Param("currentStart") LocalDateTime currentStart,
            @Param("previousStart") LocalDateTime previousStart);

    /**
     * 获取审核总数
     */
    Integer selectTotalReviews(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 按状态统计数量
     */
    Integer countByStatus(
            @Param("teacherId") Long teacherId,
            @Param("status") Integer status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
// 在 ReviewRecordMapper 接口中添加
List<Map<String, Object>> selectAllStatusReviews(@Param("teacherId") Long teacherId,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 获取详细数据（分页）
     */
    List<TrendDataVO> selectDetailData(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDateTime,
            @Param("offset") Integer offset,
            @Param("size") Integer size);

    /**
     * 获取所有详细数据（导出用）
     */
    List<TrendDataVO> selectAllDetailData(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDateTime);

    /**
     * 获取审核状态分布
     */
    List<ReviewStatusVO> selectReviewStatusDistribution(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}

