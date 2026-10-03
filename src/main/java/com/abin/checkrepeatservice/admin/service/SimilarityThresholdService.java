package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.SimilarityThresholdUpdateReq;
import com.abin.checkrepeatservice.admin.vo.SimilarityThresholdVO;

/**
 * 相似度阈值设置服务
 */
public interface SimilarityThresholdService {

    /**
     * 读取当前阈值设置。表为空时全局阈值返回 yml 兜底值。
     */
    SimilarityThresholdVO getThresholds();

    /**
     * 整体覆盖阈值设置。参数非法时抛 BusinessException（对应 HTTP 400）。
     */
    void updateThresholds(SimilarityThresholdUpdateReq req);

}
