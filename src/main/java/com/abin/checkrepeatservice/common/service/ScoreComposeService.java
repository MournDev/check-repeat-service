package com.abin.checkrepeatservice.common.service;

import java.math.BigDecimal;

/**
 * 总评成绩加权合成服务（论文全生命周期 P5）。
 *
 * 合成公式：总评 = 指导分 × w1% + 评阅分 × w2% + 答辩分 × w3%（scale 2 HALF_UP）。
 *
 * 三个数据源：
 *  - 指导分：paper_check_signoff 最新一条 qualified=1 的 guidance_score（缺失抛 40914）；
 *  - 评阅分：review_assignment 中 status=2（已提交）任务的平均分（无记录按 0 分参与）；
 *  - 答辩分：终期批次（batch_type=final）result=pass 的 defense_record.score（无记录按 0 分）。
 *
 * 权重来自 system_param 三列 guidance_weight/review_weight/defense_weight（百分比），
 * 未配置回落 20/30/50，三项合计 ≠ 100 抛 40915。
 *
 * 兜底链路：答辩接线处合成失败不阻断阶段门，管理端 POST /api/admin/thesis/{paperId}/recompute-score
 * 可随时重算回写。
 */
public interface ScoreComposeService {

    /**
     * 按当前权重配置合成论文总评成绩。
     *
     * @param paperId 论文ID
     * @return 总评成绩（scale 2，HALF_UP）
     */
    BigDecimal compose(Long paperId);
}
