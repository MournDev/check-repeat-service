package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学生分组（教师端「学生分组管理」）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("student_group")
public class StudentGroup extends BaseEntity {

    /**
     * 所属教师ID（关联sys_user.id）
     */
    @TableField("teacher_id")
    private Long teacherId;

    /**
     * 分组名称
     */
    @TableField("name")
    private String name;

    /**
     * 分组描述
     */
    @TableField("description")
    private String description;
}
