package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论文超期催办记录（Wave 3）：对应 paper_reminder_log 表。
 *
 * <p>该表是纯留痕表，没有 is_deleted / update_by 等审计列，
 * 所以不继承 BaseEntity，避免 MyBatis-Plus 拼出不存在的列。</p>
 */
@Data
@TableName("paper_reminder_log")
public class PaperReminderLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 论文ID（paper_info.id） */
    private Long paperId;

    /** 被催办导师ID */
    private Long teacherId;

    /** 操作管理员ID */
    private Long operatorId;

    /** 操作人角色：ADMIN / SUPER_ADMIN */
    private String operatorRole;

    /** 催办渠道：IN_APP / EMAIL / BOTH */
    private String remindChannel;

    /** 催办文案 */
    private String remindMessage;

    /** 催办时的超期天数快照 */
    private Integer overdueDays;

    /** 催办时间 */
    private LocalDateTime createTime;
}
