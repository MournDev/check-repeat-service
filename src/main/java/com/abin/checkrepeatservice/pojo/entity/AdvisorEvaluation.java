package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导师评价实体：对应 advisor_evaluation 表（论文全生命周期 P6a）
 *
 * 学生对指导教师的结构化评价：5 维度 1-5 星 + 预设标签 JSON + 选填文字评语。
 * 关键规则：
 *  - 每篇论文一次：uk_paper_id 唯一约束兜底，提交后不可改（防施压改评）；
 *  - teacher_id 是提交时刻 paper_info.teacher_id 的快照，后续改派导师不影响统计归属；
 *  - student_id 仅管理端实名可见（防滥用追溯），教师端 API 只做聚合、永不返回单条。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("advisor_evaluation")
public class AdvisorEvaluation extends BaseEntity {

    /**
     * 论文ID（关联 paper_info.id，唯一）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("paper_id")
    private Long paperId;

    /**
     * 评价学生ID（关联 sys_user.id；仅管理端实名可见）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("student_id")
    private Long studentId;

    /**
     * 被评价导师ID（关联 sys_user.id，提交时取 paper_info.teacher_id 快照）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("teacher_id")
    private Long teacherId;

    /**
     * 指导及时性（1-5星）
     */
    @TableField("score_timeliness")
    private Integer scoreTimeliness;

    /**
     * 反馈质量（1-5星）
     */
    @TableField("score_quality")
    private Integer scoreQuality;

    /**
     * 责任心态度（1-5星）
     */
    @TableField("score_attitude")
    private Integer scoreAttitude;

    /**
     * 专业能力（1-5星）
     */
    @TableField("score_professional")
    private Integer scoreProfessional;

    /**
     * 总体满意度（1-5星；导师评价分 = avg(score_overall)/5×100）
     */
    @TableField("score_overall")
    private Integer scoreOverall;

    /**
     * 预设标签（JSON 数组字符串，元素为字典 advisor_eval_tag 的 dict_value）
     */
    @TableField("tags")
    private String tags;

    /**
     * 文字评语（选填，jsoup 纯文本清洗后落库）
     */
    @TableField("comment")
    private String comment;
}
