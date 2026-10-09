package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 相似度阈值设置更新请求。
 *
 * 语义是【整体覆盖】：categoryThresholds 是提交后的完整集合，
 * 不在其中的学院/专业/学科/论文类型配置会被删除。前端新增项带的临时 id 与展示用
 * categoryName 都会被忽略，服务端只看自然键
 * (categoryType, paperType, collegeId, majorId, subjectId)。
 */
@Data
public class SimilarityThresholdUpdateReq {

    /**
     * 全局相似度阈值（0-100）
     */
    private Integer globalThreshold;

    /**
     * 学院/专业/学科/论文类型阈值完整集合
     */
    private List<CategoryItem> categoryThresholds;

    @Data
    public static class CategoryItem {

        /**
         * 分类类型：college-学院，major-专业，subject-学科领域，paper_type-论文类型
         *
         * ⚠️ 加 subject / paper_type 这类新档位时，**旧后端会拒绝整批提交**
         * （它的白名单里没有新值）。所以后端必须先发、前端后发；回滚要两个仓库配对回滚。
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
         * 学科领域ID（categoryType=subject 时必填）
         *
         * 门类与二级学科都填在这个字段里 —— 两种层级共用 subject 这一个类型值，
         * 靠 id 指向哪一行区分。二级没单独配时由其所属门类继承。
         */
        private Long subjectId;

        /**
         * 论文类型（PaperTypeEnum 的 code），可空。
         *
         * 空 = 该行适用于**全部**论文类型（存量行的形态，务必继续传空而不是传 "all" 之类的哨兵值）；
         * 非空 = 只在对应类型的论文上生效。
         * categoryType=paper_type 时必填，且此时三个 id 必须全为空。
         */
        private String paperType;

        /**
         * 相似度阈值（0-100）
         */
        private Integer threshold;

    }

}
