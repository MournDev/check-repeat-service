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
 * <p>比对库准入（P4 已落地）：本服务只负责正文入库与路径回填，不决定是否作为
 * 比对源。是否准入由 paper_info.library_admitted 控制：存量已入库论文由迁移脚本
 * 宽容回填为 1；新论文只会在归档（finalized → archived）时置 1，因此草稿、
 * 未定稿论文不会成为他人查重的比对源。</p>
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
                // 带分词版本头落库：英文归一/停用词升级后，引擎只认当前版本的缓存，
                // 旧版本文件自动回落原文实时分词，避免新旧词表混算
                segmentedPath = paperContentMinioService.storeSegmentedText(
                        IKAnalyzerUtils.SEGMENT_VERSION_HEADER + segmentedText, paperId);
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
