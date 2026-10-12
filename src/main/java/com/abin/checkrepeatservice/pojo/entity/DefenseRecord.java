package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 答辩记录实体类（对应数据库表：defense_record，见 db/init.sql 与 db/种子数据.sql 段 10）。
 *
 * 业务规则（论文全生命周期 P3）：
 *  - 按论文归档：每篇论文在每个批次有一条记录（(batch_id, paper_id) 唯一）；
 *  - 组长汇总后写入 score/grade/result/comment；终期 result=pass 时由 Service 回写
 *    paper_info.final_score 并经 PaperStageGate 推进 paper_status；
 *  - 线下答辩由秘书/组长把线下结果录入系统，附扫描件佐证（attachment_id），
 *    状态推进与线上一致（见方案 §8「线上/线下评审统一模型」）；
 *  - result=postpone（缓辩）表示本次未能完成答辩，择期重排批次，论文状态不推进。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("defense_record")
public class DefenseRecord extends BaseEntity {

    /** 答辩结论：通过 */
    public static final String RESULT_PASS = "pass";
    /** 答辩结论：不通过（退回修改） */
    public static final String RESULT_FAIL = "fail";
    /** 答辩结论：缓辩（择期重排批次） */
    public static final String RESULT_POSTPONE = "postpone";

    /**
     * 批次ID（关联defense_batch.id）
     */
    @TableField("batch_id")
    private Long batchId;

    /**
     * 论文ID（关联paper_info.id）
     */
    @TableField("paper_id")
    private Long paperId;

    /**
     * 学生ID（关联sys_user.id）
     */
    @TableField("student_id")
    private Long studentId;

    /**
     * 答辩顺序号（同批次内人工编排，可空）
     */
    @TableField("defense_order")
    private Integer defenseOrder;

    /**
     * 综合得分（0-100，组长汇总后写入）
     */
    @TableField("score")
    private BigDecimal score;

    /**
     * 等级：优/良/中/及格/不及格
     */
    @TableField("grade")
    private String grade;

    /**
     * 结论：pass-通过 / fail-不通过 / postpone-缓辩
     */
    @TableField("result")
    private String result;

    /**
     * 答辩记录（纯文本，入库前已做XSS清洗）
     */
    @TableField("comment")
    private String comment;

    /**
     * 线下答辩表扫描件（关联file_info.id，可空）
     */
    @TableField("attachment_id")
    private Long attachmentId;

    /**
     * 结论提交人ID（通常为组长，关联sys_user.id）
     */
    @TableField("submit_by")
    private Long submitBy;

    /**
     * 结论提交时间
     */
    @TableField("submit_time")
    private LocalDateTime submitTime;
}
