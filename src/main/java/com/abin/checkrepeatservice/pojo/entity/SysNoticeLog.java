package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_notice_log")
public class SysNoticeLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String toEmail;
    private String subject;
    private String content;
    private String noticeType;
    private Boolean success;
    private String errorMsg;

    private Long relatedId;        // 关联的业务ID（论文ID、用户ID等）
    private String relatedType;    // 关联的业务类型（PAPER、USER等）

    private LocalDateTime sendTime;
    private LocalDateTime createTime;
}