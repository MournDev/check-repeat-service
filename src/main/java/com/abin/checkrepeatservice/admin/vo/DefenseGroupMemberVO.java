package com.abin.checkrepeatservice.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 答辩组成员名单 VO（论文全生命周期 P3）。
 *
 * 管理端 GET /api/admin/defense/batch/{id}/members 与
 * 教师端 GET /api/teacher/defense/batch/{id}/members 共用。
 * 排序约定：组长（leader）在前，成员（member）在后，同角色按加入时间升序。
 */
@Data
public class DefenseGroupMemberVO {

    /**
     * 成员记录ID（defense_group_member.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long batchId;

    /**
     * 教师ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long teacherId;

    /**
     * 教师姓名（无真名时回退用户名）
     */
    private String teacherName;

    /**
     * 教师用户名（学号/工号）
     */
    private String teacherUsername;

    /**
     * 组角色：leader-组长 / member-成员
     */
    private String groupRole;

    private String groupRoleDesc;

    /**
     * 加入答辩组时间
     */
    private LocalDateTime createTime;
}
