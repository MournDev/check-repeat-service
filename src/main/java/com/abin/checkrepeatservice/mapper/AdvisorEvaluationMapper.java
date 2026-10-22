package com.abin.checkrepeatservice.mapper;

import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageItemVO;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageReq;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalStatsVO;
import com.abin.checkrepeatservice.common.datascope.DataScopeSqlFilter;
import com.abin.checkrepeatservice.pojo.entity.AdvisorEvaluation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 导师评价 Mapper（论文全生命周期 P6a）。
 *
 * 单表 CRUD 走 BaseMapper；跨表/聚合查询（管理端实名分页、教师端汇总、
 * 导师排行、学院对比）手写 XML，全部兼容 only_full_group_by，
 * 见 resources/mapper/AdvisorEvaluationMapper.xml。
 */
@Mapper
public interface AdvisorEvaluationMapper extends BaseMapper<AdvisorEvaluation> {

    /**
     * 教师端汇总：某导师的评价份数与五维均分（聚合列，无明细）
     */
    Map<String, Object> selectTeacherSummary(@Param("teacherId") Long teacherId);

    /**
     * 某导师全部评价的标签 JSON 串（Java 侧解析做频次统计；数据量小，不建 JSON_TABLE）
     */
    List<String> selectTeacherTags(@Param("teacherId") Long teacherId);

    /**
     * 管理端实名分页（联 paper_info/sys_user×2/college 出展示字段；beginDate/endDate 按评价提交时间过滤）
     */
    Page<AdvisorEvalPageItemVO> selectEvalPage(Page<AdvisorEvalPageItemVO> page,
                                               @Param("req") AdvisorEvalPageReq req,
                                               @Param("scope") DataScopeSqlFilter scope);

    /**
     * 导师排行：按导师聚合评价份数与总体均分（导师姓名 Java 侧批量回填；
     * beginDate/endDate 按评价提交时间过滤，可空=全量）
     *
     * @param scope 数据范围；null 表示全量（ALL）
     */
    List<AdvisorEvalStatsVO.TeacherRankItem> selectTeacherRank(@Param("beginDate") LocalDate beginDate,
                                                               @Param("endDate") LocalDate endDate,
                                                               @Param("scope") DataScopeSqlFilter scope);

    /**
     * 学院对比：按论文学院聚合评价份数与总体均分（college_id 为 NULL 归"未分配"；
     * beginDate/endDate 按评价提交时间过滤，可空=全量）
     */
    List<AdvisorEvalStatsVO.CollegeCompareItem> selectCollegeEvalCompare(@Param("beginDate") LocalDate beginDate,
                                                                         @Param("endDate") LocalDate endDate,
                                                                         @Param("scope") DataScopeSqlFilter scope);

    /**
     * 分段统计专用（P6b 学年归口）：某时间段内通过终期答辩的论文数
     * （paper_status_log 的 defense_passed 日志去重计数，供完成率分母）
     */
    long selectDefensePassedPaperCount(@Param("beginDate") LocalDate beginDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("scope") DataScopeSqlFilter scope);
}
