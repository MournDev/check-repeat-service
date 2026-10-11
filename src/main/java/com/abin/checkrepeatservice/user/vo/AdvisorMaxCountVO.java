package com.abin.checkrepeatservice.user.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 指导任务上限修改结果 VO。
 * 只带前端需要回显的字段；禁止直接返回 SysUser 实体（会连带密码哈希等敏感字段）。
 */
@Data
public class AdvisorMaxCountVO {

    /** 目标教师ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long targetUserId;

    /** 教师姓名 */
    private String realName;

    /** 修改后的指导任务上限 */
    private Integer maxReviewCount;
}
