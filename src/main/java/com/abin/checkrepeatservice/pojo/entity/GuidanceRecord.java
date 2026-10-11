package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 指导记录实体类（对应数据库表：guidance_record，见 db/补论文全生命周期P1.sql）。
 *
 * 业务规则（论文全生命周期 P1）：
 *  - 教师对其指导关系内的论文发起指导记录，学生确认后固化；
 *  - 同一论文内 round 自增，(paper_id, round) 唯一索引兜底并发；
 *  - content / student_feedback 入库前由 Service 层做 XSS 清洗（纯文本）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("guidance_record")
public class GuidanceRecord extends BaseEntity {

    /** 确认状态：待学生确认 */
    public static final int CONFIRM_PENDING = 0;
    /** 确认状态：学生已确认 */
    public static final int CONFIRMED = 1;
    /** 确认状态：学生有异议（预留，P1 暂不开放写入口） */
    public static final int DISPUTED = 2;

    /** 指导方式：线上 */
    public static final String TYPE_ONLINE = "online";
    /** 指导方式：线下 */
    public static final String TYPE_OFFLINE = "offline";

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
     * 指导教师ID（关联sys_user.id）
     */
    @TableField("teacher_id")
    private Long teacherId;

    /**
     * 指导轮次（同论文内自增，(paper_id, round) 唯一）
     */
    @TableField("round")
    private Integer round;

    /**
     * 指导方式：online-线上 / offline-线下
     */
    @TableField("guidance_type")
    private String guidanceType;

    /**
     * 指导时间
     */
    @TableField("guidance_time")
    private LocalDateTime guidanceTime;

    /**
     * 指导内容（纯文本，入库前已做XSS清洗）
     */
    @TableField("content")
    private String content;

    /**
     * 学生反馈（确认时填写，可空）
     */
    @TableField("student_feedback")
    private String studentFeedback;

    /**
     * 附件ID（关联file_info.id，可空）
     */
    @TableField("attachment_id")
    private Long attachmentId;

    /**
     * 确认状态：0-待学生确认 / 1-已确认 / 2-有异议
     */
    @TableField("confirm_status")
    private Integer confirmStatus;

    /**
     * 学生确认时间
     */
    @TableField("confirm_time")
    private LocalDateTime confirmTime;
}
