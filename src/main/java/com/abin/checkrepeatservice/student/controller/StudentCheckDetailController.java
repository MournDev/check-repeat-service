package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.dto.SentenceDetailResultVO;
import com.abin.checkrepeatservice.student.dto.SimilarityTrendVO;
import com.abin.checkrepeatservice.student.service.PaperCheckDetailService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端检测明细控制器：句级检测明细与重复率趋势（论文全生命周期 P5）
 * 挂在 /api/papers/** 下，服务层自行校验论文属主
 */
@Slf4j
@RestController
@RequestMapping("/api/papers")
public class StudentCheckDetailController {

    @Resource
    private PaperCheckDetailService paperCheckDetailService;

    /**
     * 句级检测明细接口：任务指标 + 全量命中句 + 来源聚合
     *
     * @param taskId 查重任务ID
     */
    @GetMapping("/check-tasks/{taskId}/sentence-detail")
    public Result<SentenceDetailResultVO> getSentenceDetail(@PathVariable Long taskId) {
        return Result.success(paperCheckDetailService.getSentenceDetail(taskId));
    }

    /**
     * 重复率趋势接口：按查重完成时间升序返回各次有效查重的重复率
     *
     * @param paperId 论文ID
     */
    @GetMapping("/{paperId}/similarity-trend")
    public Result<List<SimilarityTrendVO>> getSimilarityTrend(@PathVariable Long paperId) {
        return Result.success(paperCheckDetailService.getSimilarityTrend(paperId));
    }
}
