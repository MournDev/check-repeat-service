package com.abin.checkrepeatservice.common.service;

import com.abin.checkrepeatservice.common.utils.IKAnalyzerUtils;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 比对库入库服务（架构修复 P0）。
 *
 * <p>修复背景：P5 已实现「读 MinIO 正文 → 分词 → SimHash → 比对」的半边链路，
 * 但「论文提交/查重时把正文写入 MinIO 并回填 paper_info.content_path」的入库半边
 * 从未接线（storePaperContent 零调用、contentPath 实体还被误标 exist=false），
 * 导致本地比对库恒为空、所有查重结果恒为 0%。</p>
 *
 * <p>入库时机：查重任务提取正文成功后、引擎比对前。每次查重写入带 UUID 的新路径，
 * 与检测缓存「以 contentPath 为键、重新入库天然失效」的既有设计一致。</p>
 *
 * <p>比对库准入说明：当前每篇被查重的论文提取成功即入库（含各版本最新一次正文）。
 * 待 P4 归档落地后，应把准入节点收紧为「定稿/归档终稿」，届时在本服务增加状态判断，
 * 调用方无需改动。</p>
 */
@Service
@Slf4j
public class PaperLibraryIngestService {

    @Resource
    private PaperContentMinioService paperContentMinioService;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    /**
     * 将论文正文与 IK 分词文本写入 MinIO，并回填 paper_info 的路径列。
     *
     * @param paperId   论文ID（不可为空）
     * @param paperText Tika 提取的正文（空白则跳过，不污染比对库）
     */
    public void ingest(Long paperId, String paperText) {
        if (paperId == null) {
            throw new IllegalArgumentException("入库失败：论文ID为空");
        }
        if (paperText == null || paperText.isBlank()) {
            log.warn("论文正文为空，跳过比对库入库 - paperId: {}", paperId);
            return;
        }

        // 1. 正文写入 MinIO
        String contentPath = paperContentMinioService.storePaperContent(paperText, paperId);

        // 2. IK 分词文本写入 MinIO（分词为空时不阻断，引擎侧还有实时分词兜底）
        String segmentedPath = null;
        try {
            String segmentedText = IKAnalyzerUtils.segmentToString(paperText);
            if (segmentedText != null && !segmentedText.isBlank()) {
                segmentedPath = paperContentMinioService.storeSegmentedText(segmentedText, paperId);
            }
        } catch (Exception e) {
            // 分词文本入库失败不影响主流程：引擎读不到分词会实时分词
            log.warn("分词文本入库失败，引擎将实时分词 - paperId: {}", paperId, e);
        }

        // 3. 回填路径列（选择性更新，仅两列）
        PaperInfo update = new PaperInfo();
        update.setId(paperId);
        update.setContentPath(contentPath);
        update.setSegmentedPath(segmentedPath);
        int rows = paperInfoMapper.updateById(update);
        if (rows == 0) {
            log.error("比对库入库回填失败，论文不存在 - paperId: {}", paperId);
            throw new IllegalStateException("比对库入库失败：论文不存在 " + paperId);
        }
        log.info("论文已入本地比对库 - paperId: {}, contentPath: {}", paperId, contentPath);
    }
}
