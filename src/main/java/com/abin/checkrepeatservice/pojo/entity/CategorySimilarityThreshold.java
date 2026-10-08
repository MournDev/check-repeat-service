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
     * 分类类型：学科领域（此时 subject_id 有值）。
     *
     * 一级门类与二级学科都是 subject 表的行，所以**两种层级共用这一个类型值**，
     * 靠 subject_id 指向哪一行来区分。二级没单独配时由其所属门类继承
     * （解析顺序见 SimilarityThresholdResolverImpl）。
     */
    public static final String TYPE_SUBJECT = "subject";

    /**
     * 分类类型：college-学院，major-专业，subject-学科领域
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
     * 学科领域ID（关联 subject.id；category_type=subject 时必填）
     *
     * ⚠️ 这张表**没有唯一键**（只有 idx_type_college / idx_type_major），
     * 去重完全靠 Java 侧 SimilarityThresholdServiceImpl.validate 拼的 naturalKey，
     * 所以加 subject 维度时必须把 subjectId 也拼进去，否则两条只差学科的配置会互相覆盖。
     */
    @TableField("subject_id")
    private Long subjectId;

    /**
     * 相似度阈值（百分比整数，0-100）
     */
    @TableField("threshold")
    private Integer threshold;

}
