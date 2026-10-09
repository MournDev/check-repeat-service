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
     * 分类类型：论文类型（此时 paper_type 必填，三个 id 全为 NULL）。
     *
     * 这一档是"全校范围内某种论文类型的阈值"，排在学院之后、全局阈值之前 ——
     * 用来表达"毕业论文全校按 20%、课程论文全校按 30%"这类不区分学院的规则。
     * 想按学院再细分，则在学院/专业/学科行上填 paper_type（见 paperType 字段）。
     */
    public static final String TYPE_PAPER_TYPE = "paper_type";

    /**
     * 分类类型：college-学院，major-专业，subject-学科领域，paper_type-论文类型
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
     * ⚠️ 这张表**没有唯一键**，去重完全靠 Java 侧 SimilarityThresholdServiceImpl.validate
     * 拼的 naturalKey，所以每加一个维度都必须把它拼进 key，否则两条只差该维度的配置
     * 会互相覆盖。subjectId 与 paperType 都已拼入。
     */
    @TableField("subject_id")
    private Long subjectId;

    /**
     * 论文类型维度（取值为 PaperTypeEnum 的 code）。
     *
     * NULL = 适用于**全部**论文类型 —— 这正是本列存在之前所有存量行的形态，
     * 所以加了本列之后，既有配置的生效范围一条都没变。
     *
     * 两种用法：
     *   · 作用域行（TYPE_COLLEGE / TYPE_MAJOR / TYPE_SUBJECT）上填它 = 只对该类型生效，
     *     解析时先找带类型的行、找不到再退到 NULL 的通用行；
     *   · TYPE_PAPER_TYPE 行上必填（那一档除了类型没有别的限定）。
     *
     * ⚠️ 写入侧必须归一：空串要落成 NULL。解析器查"不带类型"的行用的是 IS NULL，
     *    一行写成 '' 既匹配不到通用档、也不会被任何带类型的查询命中，是永远失效的垃圾行。
     *    （存量修复.sql 段 1.11 里有对应的 UPDATE 兜底。）
     */
    @TableField("paper_type")
    private String paperType;

    /**
     * 相似度阈值（百分比整数，0-100）
     */
    @TableField("threshold")
    private Integer threshold;

}
