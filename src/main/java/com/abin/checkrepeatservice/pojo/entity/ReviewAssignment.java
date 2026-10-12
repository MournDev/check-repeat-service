package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评阅任务实体类（对应数据库表：review_assignment，见 db/init.sql 与 db/种子数据.sql 段 10）。
 *
 * 业务规则（论文全生命周期 P2）：
 *  - 初审通过后由管理员对同一论文分配 1~N 名评阅教师（(paper_id, submit_version, reviewer_id) 唯一）；
 *  - method=online 系统内直接评；method=offline 由评阅人本人/管理员代录结果回填 + 扫描件佐证；
 *  - review_type=blind 盲审时查询层对学生与导师信息脱敏；
 *  - 超时任务由定时任务标记 status=3 并可自动改派（原任务保留审计）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("review_assignment")
public class ReviewAssignment extends BaseEntity {

    /** 状态：待评阅 */
    public static final int STATUS_PENDING = 0;
    /** 状态：评阅中（预留，P2 未开放开始动作） */
    public static final int STATUS_IN_PROGRESS = 1;
    /** 状态：已提交 */
    public static final int STATUS_SUBMITTED = 2;
    /** 状态：已超时转派（原任务失效，保留审计） */
    public static final int STATUS_TIMEOUT = 3;

    /** 评阅类型：导师评阅 */
    public static final String TYPE_ADVISOR = "advisor";
    /** 评阅类型：交叉评阅 */
    public static final String TYPE_PEER = "peer";
    /** 评阅类型：盲审 */
    public static final String TYPE_BLIND = "blind";

    /** 评阅方式：线上 */
    public static final String METHOD_ONLINE = "online";
    /** 评阅方式：线下 */
    public static final String METHOD_OFFLINE = "offline";

    /** 分配方式：自动 */
    public static final String ASSIGN_AUTO = "auto";
    /** 分配方式：手动 */
    public static final String ASSIGN_MANUAL = "manual";

    /**
     * 论文ID（关联paper_info.id）
     */
    @TableField("paper_id")
    private Long paperId;

    /**
     * 评阅针对的论文版本号（paper_submit.submit_version，版本演进后历史评阅不失效）
     */
    @TableField("submit_version")
    private Integer submitVersion;

    /**
     * 评阅教师ID（关联sys_user.id）
     */
    @TableField("reviewer_id")
    private Long reviewerId;

    /**
     * 评阅类型：advisor-导师评阅 / peer-交叉评阅 / blind-盲审
     */
    @TableField("review_type")
    private String reviewType;

    /**
     * 评阅方式：online-线上 / offline-线下（结果回填）
     */
    @TableField("method")
    private String method;

    /**
     * 状态：0-待评阅 / 1-评阅中 / 2-已提交 / 3-已超时转派
     */
    @TableField("status")
    private Integer status;

    /**
     * 评分（0-100，线下任务可提交时回填）
     */
    @TableField("score")
    private BigDecimal score;

    /**
     * 评阅意见（线下任务可为空，以扫描件为准）
     */
    @TableField("comment")
    private String comment;

    /**
     * 线下评阅表扫描件（关联file_info.id，可空）
     */
    @TableField("attachment_id")
    private Long attachmentId;

    /**
     * 评阅截止时间（超时扫描挂定时任务）
     */
    @TableField("deadline")
    private LocalDateTime deadline;

    /**
     * 分配方式：auto-自动 / manual-手动
     */
    @TableField("assign_type")
    private String assignType;
}
