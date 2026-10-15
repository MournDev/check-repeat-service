package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageItemVO;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalPageReq;
import com.abin.checkrepeatservice.admin.dto.AdvisorEvalStatsVO;
import com.abin.checkrepeatservice.admin.service.AdminAdvisorEvalService;
import com.abin.checkrepeatservice.admin.vo.AdvisorEvalExcelVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.service.Impl.DictService;
import com.abin.checkrepeatservice.mapper.AdvisorEvaluationMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.AdvisorEvaluation;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端导师评价服务实现（论文全生命周期 P6a）。
 *
 * 关键口径：
 *  - 完成率 = 已评份数 / 可评论文数（paper_status ∈ defense_passed/finalized 且已分配导师）；
 *    传时间段时（P6b 学年/届别归口）：分子按评价提交时间过滤，分母改按该时段内
 *    defense_passed 状态日志的论文数（新口径，归口本届；存量无日志论文不参与分段统计）；
 *  - 导师评价分 = 总体均分 × 20（百分制一位小数），排行按此降序；
 *  - 低分预警：evalCount ≥ 3 且 avgOverall &lt; 3.0（与教师端掩码阈值同源，小样本不预警防误伤）；
 *  - 学院对比：college_id 为 NULL 归"未分配"，college 记录缺失归"未知学院"（与统计报表同口径）。
 */
@Slf4j
@Service
public class AdminAdvisorEvalServiceImpl implements AdminAdvisorEvalService {

    /** 低分预警阈值：总体均分低于此值（5 分制） */
    private static final BigDecimal LOW_SCORE_THRESHOLD = new BigDecimal("3.0");

    /** 低分预警最小样本量：小样本均分波动大，不预警防误伤 */
    private static final long LOW_SCORE_MIN_COUNT = 3L;

    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private AdvisorEvaluationMapper advisorEvaluationMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private DictService dictService;

    @Override
    public Result<Page<AdvisorEvalPageItemVO>> page(AdvisorEvalPageReq req) {
        Page<AdvisorEvalPageItemVO> page = advisorEvaluationMapper.selectEvalPage(
                new Page<>(req.getPageNum(), req.getPageSize()), req);
        // XML 只出 tagsJson 原始串，这里解析进 tags（防前端各处重复解析 JSON）
        page.getRecords().forEach(item -> item.setTags(parseTags(item.getTagsJson())));
        return Result.success(page);
    }

    @Override
    public Result<AdvisorEvalStatsVO> stats(LocalDate beginDate, LocalDate endDate) {
        AdvisorEvalStatsVO vo = new AdvisorEvalStatsVO();

        // 1. 完成率：已评份数 / 可评论文数
        //    无时间段：分子全量评价，分母=当前状态 defense_passed/finalized 且已分配导师（P6a 原口径）；
        //    有时间段：分子按评价提交时间过滤，分母=该时段内 defense_passed 状态日志的论文数
        //    （按"何时通过终期答辩"归口届别；存量无日志论文不参与分段统计，避免跨届混算失真）
        long totalEvaluations = advisorEvaluationMapper.selectCount(applyCreateTimeRange(new LambdaQueryWrapper<>(),
                beginDate, endDate));
        long eligiblePapers;
        boolean hasRange = beginDate != null || endDate != null;
        if (hasRange) {
            eligiblePapers = advisorEvaluationMapper.selectDefensePassedPaperCount(beginDate, endDate);
        } else {
            eligiblePapers = paperInfoMapper.selectCount(new LambdaQueryWrapper<PaperInfo>()
                    .in(PaperInfo::getPaperStatus,
                            DictConstants.PaperStatus.DEFENSE_PASSED, DictConstants.PaperStatus.FINALIZED)
                    .isNotNull(PaperInfo::getTeacherId));
        }
        // @TableLogic 自动附加 is_deleted=0；uk_paper_id 保证一份评价对应一篇论文
        vo.setEligiblePapers(eligiblePapers);
        vo.setTotalEvaluations(totalEvaluations);
        vo.setCompletionRate(eligiblePapers == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(totalEvaluations * 100.0 / eligiblePapers)
                        .setScale(2, RoundingMode.HALF_UP));

        // 2. 导师排行：XML 聚合出 teacherId/evalCount/avgOverall，姓名与评价分 Java 侧回填
        List<AdvisorEvalStatsVO.TeacherRankItem> rank = advisorEvaluationMapper.selectTeacherRank(beginDate, endDate);
        fillTeacherNames(rank);
        rank.forEach(item -> {
            if (item.getAvgOverall() != null) {
                item.setAdvisorScore(item.getAvgOverall()
                        .multiply(BigDecimal.valueOf(20))
                        .setScale(1, RoundingMode.HALF_UP));
            }
            item.setLowScore(isLowScore(item.getEvalCount(), item.getAvgOverall()));
        });
        rank.sort(Comparator.comparing(AdvisorEvalStatsVO.TeacherRankItem::getAdvisorScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        vo.setTeacherRank(rank);

        // 3. 低分预警名单：从排行过滤（同源数据，不重复查库）
        vo.setLowScoreTeachers(rank.stream()
                .filter(item -> Boolean.TRUE.equals(item.getLowScore()))
                .toList());

        // 4. 学院对比：NULL 学院归"未分配"，记录缺失归"未知学院"，按均分降序
        List<AdvisorEvalStatsVO.CollegeCompareItem> collegeCompare =
                advisorEvaluationMapper.selectCollegeEvalCompare(beginDate, endDate);
        collegeCompare.forEach(item -> {
            if (item.getCollegeId() == null) {
                item.setCollegeName("未分配");
            } else if (!StringUtils.hasText(item.getCollegeName())) {
                item.setCollegeName("未知学院");
            }
        });
        collegeCompare.sort(Comparator.comparing(AdvisorEvalStatsVO.CollegeCompareItem::getAvgOverall,
                Comparator.nullsLast(Comparator.reverseOrder())));
        vo.setCollegeCompare(collegeCompare);

        return Result.success(vo);
    }

    /**
     * 评价提交时间范围过滤（闭开区间：[beginDate 00:00, endDate+1天 00:00)，含 endDate 当天）
     */
    private LambdaQueryWrapper<AdvisorEvaluation> applyCreateTimeRange(LambdaQueryWrapper<AdvisorEvaluation> wrapper,
                                                                       LocalDate beginDate, LocalDate endDate) {
        if (beginDate != null) {
            wrapper.ge(AdvisorEvaluation::getCreateTime, beginDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.lt(AdvisorEvaluation::getCreateTime, endDate.plusDays(1).atStartOfDay());
        }
        return wrapper;
    }

    @Override
    public void export(AdvisorEvalPageReq req, HttpServletResponse response) {
        // 全量查明细（过滤项与列表完全同口径：导师/学院/分数段/时间段）：
        // MP 分页插件约定 size < 0 时不追加 LIMIT
        req.setPageNum(1);
        req.setPageSize(-1);
        List<AdvisorEvalPageItemVO> all = advisorEvaluationMapper
                .selectEvalPage(new Page<>(1, -1), req)
                .getRecords();

        Map<String, String> labelMap = dictService.getDictValueMap(DictConstants.DICT_ADVISOR_EVAL_TAG);
        List<AdvisorEvalExcelVO> rows = all.stream().map(item -> {
            AdvisorEvalExcelVO row = new AdvisorEvalExcelVO();
            row.setPaperTitle(item.getPaperTitle());
            row.setStudentName(item.getStudentName());
            row.setTeacherName(item.getTeacherName());
            row.setCollegeName(item.getCollegeId() == null ? "未分配"
                    : (StringUtils.hasText(item.getCollegeName()) ? item.getCollegeName() : "未知学院"));
            row.setScoreTimeliness(item.getScoreTimeliness());
            row.setScoreQuality(item.getScoreQuality());
            row.setScoreAttitude(item.getScoreAttitude());
            row.setScoreProfessional(item.getScoreProfessional());
            row.setScoreOverall(item.getScoreOverall());
            // 标签转中文名顿号拼接；字典被删的历史标签原样展示裸码
            row.setTags(parseTags(item.getTagsJson()).stream()
                    .map(t -> labelMap.getOrDefault(t, t))
                    .collect(Collectors.joining("、")));
            row.setComment(item.getComment());
            row.setCreateTime(item.getCreateTime() == null ? null : item.getCreateTime().format(EXPORT_TIME_FORMATTER));
            return row;
        }).toList();

        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = "导师评价明细_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
            EasyExcel.write(response.getOutputStream(), AdvisorEvalExcelVO.class)
                    .sheet("导师评价明细")
                    .doWrite(rows);
            log.info("导出导师评价明细成功: {}行", rows.size());
        } catch (Exception e) {
            log.error("导出导师评价明细失败", e);
            try {
                response.getWriter().write("{\"code\":500,\"message\":\"导出失败：" + e.getMessage() + "\"}");
            } catch (Exception ex) {
                log.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 批量回填导师姓名（realName 优先，空回落 username，与其他 VO 同口径）
     */
    private void fillTeacherNames(List<AdvisorEvalStatsVO.TeacherRankItem> rank) {
        List<Long> teacherIds = rank.stream()
                .map(AdvisorEvalStatsVO.TeacherRankItem::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (teacherIds.isEmpty()) {
            return;
        }
        Map<Long, SysUser> teacherMap = sysUserMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        rank.forEach(item -> {
            SysUser teacher = item.getTeacherId() == null ? null : teacherMap.get(item.getTeacherId());
            if (teacher != null) {
                item.setTeacherName(StringUtils.hasText(teacher.getRealName())
                        ? teacher.getRealName() : teacher.getUsername());
            }
        });
    }

    private boolean isLowScore(Long evalCount, BigDecimal avgOverall) {
        return evalCount != null && evalCount >= LOW_SCORE_MIN_COUNT
                && avgOverall != null && avgOverall.compareTo(LOW_SCORE_THRESHOLD) < 0;
    }

    /**
     * 标签 JSON 串 → dict_value 列表（null/空串/坏 JSON 统一归空列表，不阻断查询）
     */
    private List<String> parseTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return List.of();
        }
        List<String> tags = JSON.parseArray(tagsJson, String.class);
        return tags == null ? List.of() : tags;
    }
}
