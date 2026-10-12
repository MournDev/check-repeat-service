package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 查重报告导师签核实体：对应 paper_check_signoff 表（论文全生命周期 P5）
 *
 * 导师对论文最新一次查重结果做「合格/不合格」签核并给出指导评分（0-100）；
 * check.signoff.required=true 时，管理端评阅分配前必须存在 qualified=1 的签核记录。
 * 唯一键 (paper_id, task_id)：论文重新提交再检产生新任务后可再次签核。
 * guidance_score 同时是成绩加权合成（ScoreComposeService）中"指导分"的数据源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("paper_check_signoff")
public class PaperCheckSignoff extends BaseEntity {

    /**
     * 论文ID（关联 paper_info.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("paper_id")
    private Long paperId;

    /**
     * 查重任务ID（关联 check_task.id，签核对象）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("task_id")
    private Long taskId;

    /**
     * 查重报告ID（关联 check_report.id，可空）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("report_id")
    private Long reportId;

    /**
     * 签核导师ID（关联 sys_user.id，必须是论文 teacher_id 本人）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("advisor_id")
    private Long advisorId;

    /**
     * 签核结论（1-合格放行 / 0-不合格退回修改）
     */
    @TableField("qualified")
    private Integer qualified;

    /**
     * 指导评分（0-100，成绩加权合成的指导分数据源）
     */
    @TableField("guidance_score")
    private BigDecimal guidanceScore;

    /**
     * 签核意见
     */
    @TableField("comment")
    private String comment;

    /**
     * 签核时间
     */
    @TableField("sign_time")
    private LocalDateTime signTime;
}
