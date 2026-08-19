package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.pojo.entity.CheckResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 论文查重结果 Mapper 接口：定义查重结果相关的数据库操作
 */
@Mapper
public interface CheckResultMapper extends BaseMapper<CheckResult> {

    /**
     * 按分组类型（专业/年级）统计查重结果（合格/不合格/平均重复率）
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param groupType 分组类型（MAJOR-专业，GRADE-年级）
     * @param majorId 专业ID（可选）
     * @param grade 年级（可选）
     * @return 统计结果列表（含分组名、合格数、不合格数、平均重复率）
     */
    @MapKey("groupName") // 指定Map的键为groupName字段
    List<Map<String, Object>> statByGroup(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("groupType") String groupType,
            @Param("majorId") Long majorId,
            @Param("grade") Integer grade
    );

    /**
     * 查询高重复率论文列表（重复率超过阈值）
     * @param threshold 重复率阈值（如80表示80%）
     * @param limit 返回记录数限制
     * @return 高重复率论文列表
     */
    List<CheckResult> selectHighSimilarityPapers(
            @Param("threshold") BigDecimal threshold,
            @Param("limit") Integer limit
    );

    /**
     * 按时间范围统计查重次数和平均重复率
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 统计结果（总查重次数、平均重复率）
     */
    Map<String, Object> statCheckSummary(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

}