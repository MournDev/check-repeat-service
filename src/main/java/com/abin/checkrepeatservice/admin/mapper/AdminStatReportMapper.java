package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.admin.dto.CollegeCompareVO;
import com.abin.checkrepeatservice.admin.dto.MonthlyTrendVO;
import com.abin.checkrepeatservice.admin.dto.SimilarityDistributionVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 管理端全校统计报表 Mapper（论文全生命周期 P5）。
 *
 * 三个聚合查询均需跨表/聚合，单表 Wrapper 无法表达，沿用 AdminReportMapper 的
 * XML 手写模式；SQL 见 resources/mapper/AdminStatReportMapper.xml。
 * 全部查询兼容 MySQL only_full_group_by：SELECT 仅含分组列与聚合列。
 */
@Mapper
public interface AdminStatReportMapper {

    /**
     * 查重结果分布：按 repeat_rate 十分桶计数（派生子表先打桶再 GROUP BY）
     */
    List<SimilarityDistributionVO> selectSimilarityDistribution();

    /**
     * 按学院统计平均相似度与论文数（college_id 为 NULL 的行由 Java 侧归"未分配"）
     */
    List<CollegeCompareVO> selectCollegeCompare();

    /**
     * 按月统计查重次数与平均重复率（全量月份升序，Java 侧截取最近 12 个月）
     */
    List<MonthlyTrendVO> selectMonthlyTrend();
}
