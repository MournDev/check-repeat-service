package com.abin.checkrepeatservice.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

// 待办事项DTO
@Data
public class TodoItemDTO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    private String title;
    private String description;
    private Boolean completed;
    private String priority;        // high, normal, low
    private Date dueDate;
    private Date createTime;

    /** 待办类型枚举：REVISION_REQUIRED / REVIEW_PENDING / CHECK_COMPLETED / DEFENSE_SCHEDULED / EVALUATION_PENDING / DEADLINE_REMINDER */
    private String type;
    /** 业务实体类型：PAPER / DEFENSE / EVALUATION / CHECK_TASK */
    private String bizType;
    /** 业务实体ID（配合 actionCode 供前端做路由映射，不再靠标题正则猜去向）。
     *  雪花ID超出 JS 安全整数范围，统一按字符串输出。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long bizId;
    /** 动作编码：REVISE_PAPER / RESUBMIT_PAPER / VIEW_PAPER / VIEW_DEFENSE / GO_EVALUATION */
    private String actionCode;
}
