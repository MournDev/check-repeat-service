package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.pojo.entity.PaperSubmit;
import com.abin.checkrepeatservice.user.vo.PaperAdvisorTaskVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 学生论文提交 Mapper 接口：定义提交相关的数据库操作方法
 */
@Mapper
public interface PaperSubmitMapper extends BaseMapper<PaperSubmit> {

    /**
     * 统计指定时间范围内、按维度（日/周/月）分组的提交人数
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param statDimension 统计维度（DAY/WEEK/MONTH）
     * @param majorId 专业ID（可选，null 查所有）
     * @param grade 年级（可选，null 查所有）
     * @return key：时间维度（如“2024-09-01”），value：提交人数
     */
    Map<String, Integer> countByTimeRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statDimension") String statDimension,
            @Param("majorId") Long majorId,
            @Param("grade") Integer grade
    );

    /**
     * 查询指定指导老师待处理的任务列表
     * @param advisorId 指导老师ID
     * @param status 任务状态（可选，null 查所有）
     * @return 指导老师待处理的任务列表
     */
    List<PaperAdvisorTaskVO> selectAdvisorTaskList(Long advisorId, Integer status);
}
