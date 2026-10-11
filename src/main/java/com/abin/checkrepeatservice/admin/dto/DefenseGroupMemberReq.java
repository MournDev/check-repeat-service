package com.abin.checkrepeatservice.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 答辩组成员分配请求（论文全生命周期 P3，管理端 POST /api/admin/defense/batch/{id}/members）。
 *
 * 约束：每批次 1 名组长 + N 名成员；teacherIds 列表中第一个或显式 leaderTeacherId 视为组长，
 * 其余为成员。重复加入由 (batch_id, teacher_id) 唯一索引兜底跳过。
 */
@Data
public class DefenseGroupMemberReq {

    /**
     * 批次ID（路径参数，校验防漏传）
     */
    @NotNull(message = "批次ID不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long batchId;

    /**
     * 组长教师ID（关联sys_user.id，建议显式指定；为空时取 teacherIds 第一个）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long leaderTeacherId;

    /**
     * 答辩组教师ID列表（含组长，至少 1 人）
     */
    @NotEmpty(message = "答辩组教师列表不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private List<Long> teacherIds;

    /**
     * 角色：若与 leaderTeacherId 重合则强制 leader，其余按本字段
     * （默认 member）
     */
    @Pattern(regexp = "leader|member", message = "组角色只能是 leader 或 member")
    private String defaultRole;
}
