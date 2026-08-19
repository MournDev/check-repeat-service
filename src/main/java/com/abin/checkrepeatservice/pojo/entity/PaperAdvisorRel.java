package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 论文-指导老师关联实体类（对应paper_advisor_rel表）
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("paper_advisor_rel")
public class PaperAdvisorRel extends BaseEntity {

    /**
     * 论文ID（关联paper_submit.id，唯一）
     */
    @NotNull(message = "论文ID不能为空")
    @Positive(message = "论文ID必须为正整数")
    @TableField("paper_id")
    private Long paperId;

    /**
     * 指导老师ID（关联sys_user.id）
     */
    @NotNull(message = "指导老师ID不能为空")
    @Positive(message = "指导老师ID必须为正整数")
    @TableField("advisor_id")
    private Long advisorId;

    /**
     * 指导轮次（如“第一轮”“第二轮”）
     */
    @NotNull(message = "指导轮次不能为空")
    @Positive(message = "指导轮次必须为正整数")
    @TableField("advisor_round")
    private Integer advisorRound;

    /**
     * 学生所属专业ID（关联major.id，与paper_submit一致）
     */
    @NotNull(message = "学生专业ID不能为空")
    @Positive(message = "学生专业ID必须为正整数")
    @TableField("student_major_id")
    private Long studentMajorId;

    /**
     * 指导老师关联的专业ID（关联major.id，与sys_user_major一致）
     */
    @NotNull(message = "指导老师关联专业ID不能为空")
    @Positive(message = "指导老师关联专业ID必须为正整数")
    @TableField("advisor_major_id")
    private Long advisorMajorId;

    /**
     * 关联状态：1-有效，0-无效
     */
    @NotNull(message = "关联状态不能为空")
    @TableField("rel_status")
    private Integer relStatus = 1;
}
