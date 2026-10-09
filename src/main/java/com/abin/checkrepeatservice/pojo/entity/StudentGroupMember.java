package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生分组成员。
 * 不继承 BaseEntity：本表物理删除（同 teacher_subject 的设计理由——
 * (group_id, student_id) 复合唯一键若叠加软删，移出后重新加入会撞唯一键），
 * 因此没有 is_deleted 等审计列，MyMetaObjectHandler 也不会填充本类。
 */
@Data
@TableName("student_group_member")
public class StudentGroupMember {

    /**
     * 主键ID（雪花ID）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 分组ID（关联student_group.id）
     */
    @TableField("group_id")
    private Long groupId;

    /**
     * 学生ID（关联sys_user.id）
     */
    @TableField("student_id")
    private Long studentId;

    /**
     * 加入时间（插入时字段为 null，由数据库默认值 CURRENT_TIMESTAMP 落库）
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
