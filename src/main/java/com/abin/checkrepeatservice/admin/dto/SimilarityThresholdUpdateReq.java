package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 相似度阈值设置更新请求。
 *
 * 语义是【整体覆盖】：categoryThresholds 是提交后的完整集合，
 * 不在其中的学院/专业配置会被删除。前端新增项带的临时 id 与展示用
 * categoryName 都会被忽略，服务端只看自然键 (categoryType, collegeId, majorId)。
 */
@Data
public class SimilarityThresholdUpdateReq {

    /**
     * 全局相似度阈值（0-100）
     */
    private Integer globalThreshold;

    /**
     * 学院/专业阈值完整集合
     */
    private List<CategoryItem> categoryThresholds;

    @Data
    public static class CategoryItem {

        /**
         * 分类类型：college-学院，major-专业
         */
        private String categoryType;

        /**
         * 学院ID
         */
        private Long collegeId;

        /**
         * 专业ID（categoryType=major 时必填）
         */
        private Long majorId;

        /**
         * 相似度阈值（0-100）
         */
        private Integer threshold;

    }

}
