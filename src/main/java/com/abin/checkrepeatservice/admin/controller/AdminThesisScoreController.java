package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.service.ScoreComposeService;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 管理端论文总评成绩控制器（论文全生命周期 P5）：仅管理员角色可访问。
 *
 * 定位是成绩合成的管理端兜底入口：答辩接线处合成失败（缺指导分 40914 / 权重非法 40915）
 * 不阻断阶段门，final_score 留空；待导师补签 / 管理员修正 system_param 权重后，
 * 从这里重算回写，不需要重放答辩结论。
 */
@RestController
@RequestMapping("/api/admin/thesis")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminThesisScoreController {

    @Resource
    private ScoreComposeService scoreComposeService;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    /**
     * 重算论文总评成绩：按当前权重配置与最新数据源（合格签核/已提交评阅/终期答辩pass）合成，
     * 并回写 paper_info.final_score，返回新成绩。
     */
    @PostMapping("/{paperId}/recompute-score")
    @OperationLog(type = "admin_thesis_score_recompute", description = "管理员重算论文总评成绩")
    public Result<BigDecimal> recomputeScore(@PathVariable("paperId") Long paperId) {
        // 合成自带硬校验：论文不存在 404、缺指导分 40914、权重非法 40915，失败即整体不回写
        BigDecimal finalScore = scoreComposeService.compose(paperId);

        PaperInfo updatePaper = new PaperInfo();
        updatePaper.setId(paperId);
        updatePaper.setFinalScore(finalScore);
        paperInfoMapper.updateById(updatePaper);

        return Result.success("总评成绩重算完成", finalScore);
    }
}
