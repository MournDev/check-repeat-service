package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.DefenseBatchMapper;
import com.abin.checkrepeatservice.admin.mapper.DefenseRecordMapper;
import com.abin.checkrepeatservice.admin.mapper.SystemParamMapper;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.ScoreComposeService;
import com.abin.checkrepeatservice.pojo.entity.DefenseBatch;
import com.abin.checkrepeatservice.pojo.entity.DefenseRecord;
import com.abin.checkrepeatservice.pojo.entity.PaperCheckSignoff;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.ReviewAssignment;
import com.abin.checkrepeatservice.pojo.entity.SystemParam;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.mapper.PaperCheckSignoffMapper;
import com.abin.checkrepeatservice.teacher.mapper.ReviewAssignmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 总评成绩加权合成实现（论文全生命周期 P5）。
 *
 * 与 SimilarityThresholdResolverImpl 同包惯例：common/service 下接口 + Impl 同包子包。
 *
 * 数据源口径（均已在 P2/P3 代码中核实）：
 *  - 指导分：paper_check_signoff 最新一条 qualified=1 的 guidance_score。取"最新合格签核"而非
 *    "最新签核"——论文退回再检后导师会补签新的合格记录，旧的不合格记录不作为成绩数据源。
 *  - 评阅分：review_assignment.status=2（STATUS_SUBMITTED 已提交，P2 常量核实）的 score 平均值。
 *    按 0 分而非报错处理：多评阅人场景允许部分评阅未完成时先行合成（管理端可事后重算），
 *    is_deleted 由 @TableLogic 自动附加。
 *  - 答辩分：defense_record.result=pass 且所属 defense_batch.batch_type='final' 的 score，
 *    多条取最新（中期 pass 记录不算答辩分）；缺记录按 0 分（存量只答了中期的论文可先合成）。
 *
 * 失败语义：只在该抛的地方抛——缺指导分 40914、权重非法 40915；
 * 评阅/答辩数据缺失按 0 分参与合成（注释即契约，管理端重算可随时覆盖）。
 */
@Slf4j
@Service
public class ScoreComposeServiceImpl implements ScoreComposeService {

    /** 默认权重：指导 20 / 评阅 30 / 答辩 50（system_param 三列未配置时回落） */
    private static final BigDecimal DEFAULT_GUIDANCE_WEIGHT = new BigDecimal("20");
    private static final BigDecimal DEFAULT_REVIEW_WEIGHT = new BigDecimal("30");
    private static final BigDecimal DEFAULT_DEFENSE_WEIGHT = new BigDecimal("50");

    /** 权重合计校验基准（百分比制） */
    private static final BigDecimal WEIGHT_TOTAL = new BigDecimal("100");

    /** 合成结果保留 2 位小数，四舍五入 HALF_UP */
    private static final int SCORE_SCALE = 2;

    @Resource
    private SystemParamMapper systemParamMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private PaperCheckSignoffMapper paperCheckSignoffMapper;

    @Resource
    private ReviewAssignmentMapper reviewAssignmentMapper;

    @Resource
    private DefenseRecordMapper defenseRecordMapper;

    @Resource
    private DefenseBatchMapper defenseBatchMapper;

    @Override
    public BigDecimal compose(Long paperId) {
        PaperInfo paper = paperInfoMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除", null);
        }

        // a) 权重：system_param 单例行，三列 NULL 逐项回落默认，合计 ≠ 100 直接拒绝
        BigDecimal[] weights = resolveWeights();

        // b) 指导分：最新 qualified=1 的签核记录（缺失即 40914，硬前置）
        PaperCheckSignoff signoff = paperCheckSignoffMapper.selectOne(new LambdaQueryWrapper<PaperCheckSignoff>()
                .eq(PaperCheckSignoff::getPaperId, paperId)
                .eq(PaperCheckSignoff::getQualified, 1)
                .orderByDesc(PaperCheckSignoff::getCreateTime)
                .orderByDesc(PaperCheckSignoff::getId)
                .last("LIMIT 1"));
        if (signoff == null || signoff.getGuidanceScore() == null) {
            throw new BusinessException(ResultCode.BUSINESS_SCORE_NO_GUIDANCE);
        }

        // c) 评阅分：status=2（已提交）任务的平均分，无记录/无分值按 0 分参与合成
        BigDecimal reviewScore = BigDecimal.ZERO;
        List<ReviewAssignment> submittedReviews = reviewAssignmentMapper.selectList(
                new LambdaQueryWrapper<ReviewAssignment>()
                        .eq(ReviewAssignment::getPaperId, paperId)
                        .eq(ReviewAssignment::getStatus, ReviewAssignment.STATUS_SUBMITTED)
                        .isNotNull(ReviewAssignment::getScore));
        if (!submittedReviews.isEmpty()) {
            // Java 端求平均（而非 AVG 聚合 SQL）：条数有限，且能精确控制 scale 与舍入口径
            BigDecimal sum = submittedReviews.stream()
                    .map(ReviewAssignment::getScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            reviewScore = sum.divide(BigDecimal.valueOf(submittedReviews.size()), SCORE_SCALE, RoundingMode.HALF_UP);
        }

        // d) 答辩分：终期批次（batch_type=final）result=pass 的记录分，多条取最新，缺失按 0 分
        BigDecimal defenseScore = BigDecimal.ZERO;
        List<DefenseRecord> passRecords = defenseRecordMapper.selectList(new LambdaQueryWrapper<DefenseRecord>()
                .eq(DefenseRecord::getPaperId, paperId)
                .eq(DefenseRecord::getResult, DefenseRecord.RESULT_PASS)
                .orderByDesc(DefenseRecord::getCreateTime)
                .orderByDesc(DefenseRecord::getId));
        for (DefenseRecord record : passRecords) {
            // defense_record 本身不带 batch_type，须回查批次行判定终期；
            // 记录已按时间倒序，第一个命中的终期 pass 即最新一次通过
            DefenseBatch batch = record.getBatchId() == null ? null
                    : defenseBatchMapper.selectById(record.getBatchId());
            if (batch != null && DefenseBatch.TYPE_FINAL.equals(batch.getBatchType())) {
                if (record.getScore() != null) {
                    defenseScore = record.getScore();
                }
                break;
            }
        }

        // e) 合成：指导×w1% + 评阅×w2% + 答辩×w3%，scale 2 HALF_UP
        BigDecimal finalScore = signoff.getGuidanceScore().multiply(weights[0])
                .add(reviewScore.multiply(weights[1]))
                .add(defenseScore.multiply(weights[2]))
                .divide(WEIGHT_TOTAL, SCORE_SCALE, RoundingMode.HALF_UP);

        log.info("总评成绩合成：paperId={}，指导={}×{}，评阅={}×{}，答辩={}×{}，总评={}",
                paperId, signoff.getGuidanceScore(), weights[0], reviewScore, weights[1],
                defenseScore, weights[2], finalScore);
        return finalScore;
    }

    /**
     * 解析三项权重：system_param 单例行（selectOne 全表，单例表仅 1 条记录，
     * 与 MaintenanceStateHolder 同一读法），列值 NULL 按未配置逐项回落默认；
     * 三项合计 ≠ 100 抛 40915——带具体数值抛，管理员不用猜是哪项配错。
     */
    private BigDecimal[] resolveWeights() {
        BigDecimal guidanceWeight = DEFAULT_GUIDANCE_WEIGHT;
        BigDecimal reviewWeight = DEFAULT_REVIEW_WEIGHT;
        BigDecimal defenseWeight = DEFAULT_DEFENSE_WEIGHT;

        SystemParam param = systemParamMapper.selectOne(new LambdaQueryWrapper<>());
        if (param != null) {
            // 逐项判空回落：任一列为 NULL 只影响该项，不让"部分配置"变成"整体回默认"
            if (param.getGuidanceWeight() != null) {
                guidanceWeight = param.getGuidanceWeight();
            }
            if (param.getReviewWeight() != null) {
                reviewWeight = param.getReviewWeight();
            }
            if (param.getDefenseWeight() != null) {
                defenseWeight = param.getDefenseWeight();
            }
        }

        BigDecimal total = guidanceWeight.add(reviewWeight).add(defenseWeight);
        if (total.compareTo(WEIGHT_TOTAL) != 0) {
            throw new BusinessException(ResultCode.BUSINESS_WEIGHT_INVALID,
                    "成绩权重配置非法：指导" + guidanceWeight.stripTrailingZeros().toPlainString()
                            + "% + 评阅" + reviewWeight.stripTrailingZeros().toPlainString()
                            + "% + 答辩" + defenseWeight.stripTrailingZeros().toPlainString()
                            + "% = " + total.stripTrailingZeros().toPlainString() + "，三项合计必须等于100",
                    null);
        }
        return new BigDecimal[]{guidanceWeight, reviewWeight, defenseWeight};
    }
}
