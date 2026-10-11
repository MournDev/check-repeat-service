package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 答辩批次实体类（对应数据库表：defense_batch，见 db/补论文全生命周期P3.sql）。
 *
 * 业务规则（论文全生命周期 P3）：
 *  - 中期/终期共用一张表，batch_type 区分（mid_term / final）—— 一套代码两用；
 *  - mode=online 时 location 填会议链接，mode=offline 时 location 填教室地址；
 *  - status：0-筹备中 / 1-进行中 / 2-已结束；组长提交全部结论后由 Service 推进为已结束；
 *  - 终期批次（batch_type=final）成绩提交后由组长确认回写 paper_info.final_score，
 *    并经 PaperStageGate 推进 paper_status：reviewing → mid_defense → final_defense
 *    → defense_passed → finalized（finalized 由指导教师定稿确认，归档 P4 接）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("defense_batch")
public class DefenseBatch extends BaseEntity {

    /** 批次状态：筹备中 */
    public static final int STATUS_PREPARING = 0;
    /** 批次状态：进行中 */
    public static final int STATUS_IN_PROGRESS = 1;
    /** 批次状态：已结束（组长已汇总提交结论） */
    public static final int STATUS_FINISHED = 2;

    /** 批次类型：中期答辩 */
    public static final String TYPE_MID_TERM = "mid_term";
    /** 批次类型：终期答辩（成绩回写 final_score） */
    public static final String TYPE_FINAL = "final";

    /** 答辩方式：线上 */
    public static final String MODE_ONLINE = "online";
    /** 答辩方式：线下 */
    public static final String MODE_OFFLINE = "offline";

    /**
     * 批次编号（人工或系统生成，唯一）
     */
    @TableField("batch_no")
    private String batchNo;

    /**
     * 批次类型：mid_term-中期答辩 / final-终期答辩
     */
    @TableField("batch_type")
    private String batchType;

    /**
     * 学院ID（关联college.id，可空表示全校批次）
     */
    @TableField("college_id")
    private Long collegeId;

    /**
     * 答辩时间
     */
    @TableField("defense_date")
    private LocalDateTime defenseDate;

    /**
     * 地点：线下填教室、线上填会议链接
     */
    @TableField("location")
    private String location;

    /**
     * 答辩方式：online-线上 / offline-线下
     */
    @TableField("mode")
    private String mode;

    /**
     * 答辩组长ID（关联sys_user.id，组长主持并汇总结论）
     */
    @TableField("leader_id")
    private Long leaderId;

    /**
     * 答辩秘书ID（关联sys_user.id，负责记录与线下回填）
     */
    @TableField("secretary_id")
    private Long secretaryId;

    /**
     * 批次状态：0-筹备中 / 1-进行中 / 2-已结束
     */
    @TableField("status")
    private Integer status;

    /**
     * 备注（可空）
     */
    @TableField("remark")
    private String remark;
}
