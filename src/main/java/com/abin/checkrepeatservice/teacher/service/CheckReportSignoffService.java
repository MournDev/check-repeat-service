package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.GuidanceProgressVO;
import com.abin.checkrepeatservice.teacher.dto.PaperCheckSignoffReq;
import com.abin.checkrepeatservice.teacher.dto.PaperCheckSignoffVO;

/**
 * 教师端查重报告签核服务（论文全生命周期 P5）。
 *
 * 签核语义：导师对论文最新一次 completed 查重任务给出「合格/不合格」结论与指导评分；
 * check.signoff.required=true 时，合格签核是管理端评阅分配（P2 allocate）的前置门槛，
 * 指导评分同时是成绩加权合成（ScoreComposeService）的"指导分"数据源。
 */
public interface CheckReportSignoffService {

    /**
     * 签核论文最新一次查重报告。
     *
     * @param req 签核请求（paperId/qualified/guidanceScore 必填）
     * @return 签核记录ID（字符串）
     */
    Result<String> sign(PaperCheckSignoffReq req);

    /**
     * 查询论文最新一次签核记录。
     *
     * @param paperId 论文ID
     * @return 最新签核记录；未签核时 data 为 null
     */
    Result<PaperCheckSignoffVO> getSignoff(Long paperId);

    /**
     * 指导看板：我指导论文的状态分布 + 最近论文明细。
     */
    Result<GuidanceProgressVO> guidanceProgress();
}
