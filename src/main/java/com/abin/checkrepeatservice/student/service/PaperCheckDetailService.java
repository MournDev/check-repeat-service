package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.student.dto.SentenceDetailResultVO;
import com.abin.checkrepeatservice.student.dto.SimilarityTrendVO;

import java.util.List;

/**
 * 学生端检测明细与重复率趋势服务（论文全生命周期 P5）
 */
public interface PaperCheckDetailService {

    /**
     * 查询指定查重任务的句级检测明细（含来源聚合）
     *
     * @param taskId 查重任务ID
     * @return 任务指标 + 句级明细 + 来源聚合列表
     */
    SentenceDetailResultVO getSentenceDetail(Long taskId);

    /**
     * 查询指定论文的重复率趋势（按查重完成时间升序）
     *
     * @param paperId 论文ID
     * @return 趋势点列表
     */
    List<SimilarityTrendVO> getSimilarityTrend(Long paperId);
}
