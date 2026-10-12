package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.CheckResultMapper;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.CheckResult;
import com.abin.checkrepeatservice.pojo.entity.CheckSentenceHit;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.dto.SentenceDetailItemVO;
import com.abin.checkrepeatservice.student.dto.SentenceDetailResultVO;
import com.abin.checkrepeatservice.student.dto.SentenceDetailVO;
import com.abin.checkrepeatservice.student.dto.SimilarityTrendVO;
import com.abin.checkrepeatservice.student.mapper.CheckSentenceHitMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.PaperCheckDetailService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 学生端检测明细与重复率趋势服务实现（论文全生命周期 P5）
 */
@Slf4j
@Service
public class PaperCheckDetailServiceImpl implements PaperCheckDetailService {

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private CheckSentenceHitMapper checkSentenceHitMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private CheckResultMapper checkResultMapper;

    @Override
    public SentenceDetailResultVO getSentenceDetail(Long taskId) {
        // 1. 校验任务存在
        CheckTask task = checkTaskMapper.selectById(taskId);
        if (task == null || Integer.valueOf(1).equals(task.getIsDeleted())) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "查重任务不存在或已删除");
        }

        // 2. 校验论文存在且属主为当前学生（管理员放行）
        PaperInfo paperInfo = paperInfoMapper.selectById(task.getPaperId());
        if (paperInfo == null || Integer.valueOf(1).equals(paperInfo.getIsDeleted())) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "任务关联的论文不存在或已删除");
        }
        checkPaperOwner(paperInfo);

        // 3. 查询句级命中明细（@TableLogic 自动过滤已删除，按句序号升序）
        List<CheckSentenceHit> hits = checkSentenceHitMapper.selectList(
                new LambdaQueryWrapper<CheckSentenceHit>()
                        .eq(CheckSentenceHit::getTaskId, taskId)
                        .orderByAsc(CheckSentenceHit::getSentenceIndex));

        // 4. 组装句级明细（全量返回）
        List<SentenceDetailItemVO> items = hits.stream()
                .map(this::toItemVO)
                .collect(Collectors.toList());

        // 5. 按来源论文内存分组聚合（避免 only_full_group_by 限制）
        List<SentenceDetailVO> sources = buildSources(hits);

        // 6. 组装结果（任务指标取自 check_task，无命中时列表为空、指标照常返回）
        SentenceDetailResultVO result = new SentenceDetailResultVO();
        result.setTaskId(task.getId());
        result.setCheckRate(task.getCheckRate());
        result.setMaxPerSource(task.getMaxPerSource());
        result.setCitationRate(task.getCitationRate());
        result.setHitCount(hits.size());
        result.setItems(items);
        result.setSources(sources);
        return result;
    }

    @Override
    public List<SimilarityTrendVO> getSimilarityTrend(Long paperId) {
        // 1. 校验论文存在且属主为当前学生（管理员放行）
        PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
        if (paperInfo == null || Integer.valueOf(1).equals(paperInfo.getIsDeleted())) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }
        checkPaperOwner(paperInfo);

        // 2. 查询有效查重结果（指定列，@TableLogic 自动过滤已删除，按查重时间升序）
        List<CheckResult> results = checkResultMapper.selectList(
                new LambdaQueryWrapper<CheckResult>()
                        .select(CheckResult::getId, CheckResult::getTaskId, CheckResult::getRepeatRate,
                                CheckResult::getMaxPerSource, CheckResult::getCitationRate, CheckResult::getCheckTime)
                        .eq(CheckResult::getPaperId, paperId)
                        .isNotNull(CheckResult::getRepeatRate)
                        .orderByAsc(CheckResult::getCheckTime));

        // 3. 转换为趋势点列表
        return results.stream().map(this::toTrendVO).collect(Collectors.toList());
    }

    /**
     * 单条命中转句级明细VO
     */
    private SentenceDetailItemVO toItemVO(CheckSentenceHit hit) {
        SentenceDetailItemVO item = new SentenceDetailItemVO();
        item.setSentenceIndex(hit.getSentenceIndex());
        item.setSentenceText(hit.getSentenceText());
        item.setSimilarity(hit.getSimilarity());
        item.setIsCitation(hit.getIsCitation());
        item.setStartOffset(hit.getStartOffset());
        item.setHitPaperId(hit.getHitPaperId());
        item.setHitPaperTitle(hit.getHitPaperTitle());
        return item;
    }

    /**
     * 按来源论文聚合：命中句数、引用句数、平均相似度、字符占比
     */
    private List<SentenceDetailVO> buildSources(List<CheckSentenceHit> hits) {
        List<SentenceDetailVO> sources = new ArrayList<>();
        if (hits.isEmpty()) {
            return sources;
        }
        // 分母：全部命中句去空白字符数合计
        long totalChars = hits.stream()
                .mapToLong(hit -> nonWhitespaceLen(hit.getSentenceText()))
                .sum();
        // hitPaperId 分组（LinkedHashMap 保持句序号升序带来的来源顺序）
        Map<Long, List<CheckSentenceHit>> grouped = hits.stream()
                .filter(hit -> hit.getHitPaperId() != null)
                .collect(Collectors.groupingBy(CheckSentenceHit::getHitPaperId,
                        LinkedHashMap::new, Collectors.toList()));
        grouped.forEach((hitPaperId, group) -> {
            SentenceDetailVO source = new SentenceDetailVO();
            source.setHitPaperId(hitPaperId);
            source.setHitPaperTitle(group.get(0).getHitPaperTitle());
            source.setSentenceCount(group.size());
            source.setCitationSentenceCount((int) group.stream()
                    .filter(hit -> Integer.valueOf(1).equals(hit.getIsCitation()))
                    .count());
            source.setAvgSimilarity(avgSimilarity(group));
            long groupChars = group.stream()
                    .mapToLong(hit -> nonWhitespaceLen(hit.getSentenceText()))
                    .sum();
            // 占比 = 该来源命中句字符数 / 全部命中句字符数，保留 2 位
            BigDecimal sharePercent = totalChars == 0
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(groupChars)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(totalChars), 2, RoundingMode.HALF_UP);
            source.setSharePercent(sharePercent);
            sources.add(source);
        });
        return sources;
    }

    /**
     * 组内相似度平均值（忽略空值，保留 2 位）
     */
    private BigDecimal avgSimilarity(List<CheckSentenceHit> group) {
        List<BigDecimal> similarities = group.stream()
                .map(CheckSentenceHit::getSimilarity)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (similarities.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = similarities.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(similarities.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * 文本去空白字符后的长度
     */
    private long nonWhitespaceLen(String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }
        return text.chars().filter(c -> !Character.isWhitespace(c)).count();
    }

    /**
     * 单条查重结果转趋势VO
     */
    private SimilarityTrendVO toTrendVO(CheckResult result) {
        SimilarityTrendVO vo = new SimilarityTrendVO();
        vo.setTaskId(result.getTaskId());
        vo.setCheckTime(result.getCheckTime());
        vo.setRepeatRate(result.getRepeatRate());
        vo.setMaxPerSource(result.getMaxPerSource());
        vo.setCitationRate(result.getCitationRate());
        return vo;
    }

    /**
     * 属主校验：paper_info.studentId 等于当前用户才放行，管理员沿用现有 isAdmin 惯例放行
     */
    private void checkPaperOwner(PaperInfo paperInfo) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        boolean isOwner = paperInfo.getStudentId() != null && paperInfo.getStudentId().equals(currentUserId);
        if (!isOwner && !UserBusinessInfoUtils.isAdmin()) {
            log.warn("检测数据越权访问被拒绝 - paperId: {}, studentId: {}, currentUserId: {}",
                    paperInfo.getId(), paperInfo.getStudentId(), currentUserId);
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权限查看该论文的检测数据");
        }
    }
}
