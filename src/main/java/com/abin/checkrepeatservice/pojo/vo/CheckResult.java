package com.abin.checkrepeatservice.pojo.vo;
import com.abin.checkrepeatservice.detection.dto.SentenceHit;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 查重引擎内部结果封装（用于引擎→服务层传递数据）
 */
@Data
public class CheckResult {
    private BigDecimal similarity; // 相似度（百分比，如25.32）—— 旧口径：整篇最大余弦，保留做兼容
    private String checkSource; // 查重来源（本地库/第三方API）
    private String reportUrl; // 查重报告链接（本地库可为空）
    private String extraInfo; // 额外信息（如最相似论文标题、海明距离等）
    private boolean success; // 查重是否成功
    private String failReason; // 失败原因（仅success=false时生效）

    // ---------------- P5 引擎底座新增指标 ----------------

    /**
     * 总相似比（%）：非引用命中句字数/正文字数。
     * P5 起落库与阈值判定以本指标为准（check_rate / paper_info.similarity_rate 新口径）；
     * 为 null 时（如引擎异常降级）调用方回落 similarity 旧口径。
     */
    private BigDecimal totalSimilarityRate;

    /**
     * 单篇最大相似比（%）：单篇来源命中字数/正文字数的最大值
     */
    private BigDecimal maxPerSource;

    /**
     * 引用率（%）：句尾 [n] 引注命中句字数/正文字数，不计入总相似比
     */
    private BigDecimal citationRate;

    /**
     * 句级命中明细（句级标红数据，监听器落库 check_sentence_hit）
     */
    private List<SentenceHit> sentenceHits;
}
