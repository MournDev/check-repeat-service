package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学科/专业相似度阈值设置实体类
 * 对应数据库表：category_similarity_threshold
 *
 * 每行表示"某学院"或"某专业"的专属阈值，优先级高于全局阈值。
 * 判定逻辑见 common/service/SimilarityThresholdResolver。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("category_similarity_threshold")
public class CategorySimilarityThreshold extends BaseEntity {

    /** 分类类型：学院（此时 college_id 有值、major_id 为 null） */
    public static final String TYPE_COLLEGE = "college";

    /** 分类类型：专业（此时 major_id 有值，college_id 记录所属学院） */
    public static final String TYPE_MAJOR = "major";

    /**
     * 分类类型：college-学院，major-专业
     */
    @TableField("category_type")
    private String categoryType;

    /**
     * 学院ID（关联 college.id）
     */
    @TableField("college_id")
    private Long collegeId;

    /**
     * 专业ID（关联 major.id）
     */
    @TableField("major_id")
    private Long majorId;

    /**
     * 相似度阈值（百分比整数，0-100）
     */
    @TableField("threshold")
    private Integer threshold;

}
