package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.admin.vo.AdminReportListItemVO;
import com.abin.checkrepeatservice.admin.vo.AdminReportStatsVO;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 管理员报告查询Mapper
 * <p>
 * check_report 表自身不含论文标题/学生姓名/相似度/查重状态，筛选与展示字段全部
 * 分布在 check_task、paper_info、sys_user 上，单表 Wrapper 无法表达，故用 XML 手写关联查询。
 */
@Mapper
public interface AdminReportMapper extends BaseMapper<CheckReport> {

    /**
     * 分页查询报告列表（四表关联 + 动态筛选）
     */
    IPage<AdminReportListItemVO> selectReportPage(
            IPage<AdminReportListItemVO> page,
            @Param("paperTitle") String paperTitle,
            @Param("studentName") String studentName,
            @Param("checkStatus") String checkStatus,
            @Param("minSimilarity") BigDecimal minSimilarity,
            @Param("maxSimilarity") BigDecimal maxSimilarity);

    /**
     * 统计报告指标（与列表同一组筛选条件，保证统计与列表口径一致）
     */
    AdminReportStatsVO selectReportStats(
            @Param("paperTitle") String paperTitle,
            @Param("studentName") String studentName,
            @Param("checkStatus") String checkStatus,
            @Param("minSimilarity") BigDecimal minSimilarity,
            @Param("maxSimilarity") BigDecimal maxSimilarity);
}
