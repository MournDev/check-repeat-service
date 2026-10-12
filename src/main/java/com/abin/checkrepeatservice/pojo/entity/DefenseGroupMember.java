package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 答辩组成员实体类（对应数据库表：defense_group_member，见 db/init.sql 与 db/种子数据.sql 段 10）。
 *
 * 业务规则（论文全生命周期 P3）：
 *  - 每批次可配 1 名组长（leader）+ N 名成员（member）；
 *  - 组长主持并汇总结论，成员参与评分；
 *  - 答辩秘书字段在 defense_batch.secretary_id，不在本表（秘书负责记录与线下回填，不强制是组成员）；
 *  - (batch_id, teacher_id) 唯一索引兜底重复加入。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("defense_group_member")
public class DefenseGroupMember extends BaseEntity {

    /** 组角色：组长（主持并汇总结论） */
    public static final String ROLE_LEADER = "leader";
    /** 组角色：成员 */
    public static final String ROLE_MEMBER = "member";

    /**
     * 批次ID（关联defense_batch.id）
     */
    @TableField("batch_id")
    private Long batchId;

    /**
     * 教师ID（关联sys_user.id）
     */
    @TableField("teacher_id")
    private Long teacherId;

    /**
     * 组角色：leader-组长 / member-成员
     */
    @TableField("group_role")
    private String groupRole;
}
