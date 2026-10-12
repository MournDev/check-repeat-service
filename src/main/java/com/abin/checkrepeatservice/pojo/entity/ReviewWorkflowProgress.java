package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 多级初审投票实体类（对应数据库表：review_workflow_progress，见 db/init.sql 与 db/种子数据.sql 段 10）。
 *
 * 业务规则（论文全生命周期 P2，ReviewWorkflow 配置壳接线）：
 *  - 步骤定义仍在 review_workflow.steps（JSON），本表只记录「谁在哪一步投了什么票」；
 *  - (paper_id, step_index, approver_id) 唯一，一人一步只能投一票；
 *  - all/any 语义由 Service 层按本表投票记录裁决；任一步驳回 → 论文整体 rejected。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("review_workflow_progress")
public class ReviewWorkflowProgress extends BaseEntity {

    /** 投票结果：通过 */
    public static final int VOTE_PASS = 1;
    /** 投票结果：驳回 */
    public static final int VOTE_REJECT = 2;

    /**
     * 论文ID（关联paper_info.id）
     */
    @TableField("paper_id")
    private Long paperId;

    /**
     * 步骤序号（1-based，对应 review_workflow.steps 数组下标+1）
     */
    @TableField("step_index")
    private Integer stepIndex;

    /**
     * 审批人ID（关联sys_user.id）
     */
    @TableField("approver_id")
    private Long approverId;

    /**
     * 投票结果：1-通过 / 2-驳回
     */
    @TableField("vote_result")
    private Integer voteResult;

    /**
     * 审批意见（可空）
     */
    @TableField("opinion")
    private String opinion;

    /**
     * 投票时间
     */
    @TableField("vote_time")
    private LocalDateTime voteTime;
}
