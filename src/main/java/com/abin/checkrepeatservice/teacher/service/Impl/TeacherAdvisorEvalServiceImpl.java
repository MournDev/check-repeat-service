package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.service.Impl.DictService;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.AdvisorEvaluationMapper;
import com.abin.checkrepeatservice.teacher.dto.AdvisorEvalSummaryVO;
import com.abin.checkrepeatservice.teacher.service.TeacherAdvisorEvalService;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师端导师评价汇总服务实现（论文全生命周期 P6a）。
 *
 * 关键规则：
 *  - 掩码阈值 3：evalCount &lt; 3 时各均分/advisorScore 为 null、tagStats 为空，
 *    前端显示「样本不足，暂不展示」（1-2 份时均分可被导师对照时间线反推出是谁评的）；
 *  - 导师评价分 = avgOverall / 5 × 100（一位小数），与管理端排行同口径；
 *  - 标签频次在 Java 侧统计（数据量小，不建 JSON_TABLE），按次数降序。
 */
@Slf4j
@Service
public class TeacherAdvisorEvalServiceImpl implements TeacherAdvisorEvalService {

    /** 小样本掩码阈值：评价份数低于此值不展示均分（防反推身份） */
    private static final long MASK_THRESHOLD = 3L;

    @Resource
    private AdvisorEvaluationMapper advisorEvaluationMapper;

    @Resource
    private DictService dictService;

    @Override
    public Result<AdvisorEvalSummaryVO> summary() {
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();

        Map<String, Object> row = advisorEvaluationMapper.selectTeacherSummary(teacherId);
        long evalCount = row == null || row.get("evalCount") == null
                ? 0L : ((Number) row.get("evalCount")).longValue();

        AdvisorEvalSummaryVO vo = new AdvisorEvalSummaryVO();
        vo.setEvalCount(evalCount);

        // 小样本掩码：份数本身不敏感照常返回，分数与标签分布全部隐藏
        if (evalCount < MASK_THRESHOLD) {
            vo.setMasked(true);
            vo.setTagStats(List.of());
            return Result.success(vo);
        }

        vo.setMasked(false);
        vo.setAvgTimeliness(toBigDecimal(row.get("avgTimeliness")));
        vo.setAvgQuality(toBigDecimal(row.get("avgQuality")));
        vo.setAvgAttitude(toBigDecimal(row.get("avgAttitude")));
        vo.setAvgProfessional(toBigDecimal(row.get("avgProfessional")));
        vo.setAvgOverall(toBigDecimal(row.get("avgOverall")));
        // 百分制导师评价分 = 总体均分 × 20（一位小数）
        if (vo.getAvgOverall() != null) {
            vo.setAdvisorScore(vo.getAvgOverall()
                    .multiply(BigDecimal.valueOf(20))
                    .setScale(1, RoundingMode.HALF_UP));
        }
        vo.setTagStats(buildTagStats(teacherId));

        return Result.success(vo);
    }

    /**
     * 标签频次统计：查出该导师全部评价的标签 JSON 串，Java 侧解析累计，按次数降序
     */
    private List<AdvisorEvalSummaryVO.TagStat> buildTagStats(Long teacherId) {
        List<String> tagsJsonList = advisorEvaluationMapper.selectTeacherTags(teacherId);
        if (tagsJsonList == null || tagsJsonList.isEmpty()) {
            return List.of();
        }

        Map<String, Long> frequency = new HashMap<>();
        for (String tagsJson : tagsJsonList) {
            if (!StringUtils.hasText(tagsJson)) {
                continue;
            }
            List<String> tags = JSON.parseArray(tagsJson, String.class);
            if (tags == null) {
                continue;
            }
            for (String tag : tags) {
                frequency.merge(tag, 1L, Long::sum);
            }
        }
        if (frequency.isEmpty()) {
            return List.of();
        }

        Map<String, String> labelMap = dictService.getDictValueMap(DictConstants.DICT_ADVISOR_EVAL_TAG);
        return frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    AdvisorEvalSummaryVO.TagStat stat = new AdvisorEvalSummaryVO.TagStat();
                    stat.setTag(e.getKey());
                    // 字典被删的历史标签原样展示裸码，不丢数据
                    stat.setLabel(labelMap.getOrDefault(e.getKey(), e.getKey()));
                    stat.setCount(e.getValue());
                    return stat;
                })
                .toList();
    }

    /**
     * SQL ROUND(AVG()) 返回 BigDecimal；COUNT 无行时 AVG 为 null，这里统一防御
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }
}
