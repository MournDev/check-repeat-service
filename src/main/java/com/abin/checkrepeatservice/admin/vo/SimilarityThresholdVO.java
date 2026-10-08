package com.abin.checkrepeatservice.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.List;

/**
 * 相似度阈值设置响应。
 */
@Data
public class SimilarityThresholdVO {

    /**
     * 当前真正生效的全局阈值。表里没配置时返回 yml 兜底值，
     * 而不是前端自己的默认值 —— 免得页面显示一个并不生效的数字。
     */
    private Integer globalThreshold;

    /**
     * 学院/专业/学科阈值列表
     */
    private List<CategoryItem> categoryThresholds;

    @Data
    public static class CategoryItem {

        /**
         * 雪花ID。序列化成字符串：19 位数字超出 JS 的安全整数范围，
         * 直接给数字会在前端丢精度（与本项目其余实体的处理保持一致）。
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long id;

        private String categoryType;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long collegeId;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long majorId;

        /**
         * 学科领域ID（categoryType=subject 时有值）。
         * 学科 id 只有 13 位、本身没过 2^53，但这里同样序列化成字符串 ——
         * 前端要把它回填进与学科树共用的下拉里，统一字符串形态可以免掉
         * 「数字 id ≠ 字符串 value」这类比较坑。
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long subjectId;

        private Integer threshold;

        /**
         * 展示名，由 college / major / subject 表按 categoryType 三路反查得出，不落库。
         */
        private String categoryName;

    }

}
