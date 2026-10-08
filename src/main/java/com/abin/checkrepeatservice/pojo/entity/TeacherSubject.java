package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 教师研究方向↔学科领域关联实体类（对应 teacher_subject 表）
 *
 * 这张表是教师研究方向的**真源**。sys_user.research_direction 降级成一份
 * 「学科中文名 CSV 缓存」—— 保留它是因为有 3 个展示点和 2 处关键词匹配
 * （AdvisorAssignServiceImpl / AutoAssignmentServiceImpl）直接读那一列，
 * 让它继续可用就一行代码都不用改，且显示从 'ai' 变成「人工智能」反而更准。
 *
 * ⚠️ 同 major_subject：复合唯一键不含 is_deleted，一律物理删除，
 *    走 TeacherSubjectMapper 的 @Delete 方法，不要用 deleteById。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("teacher_subject")
public class TeacherSubject extends BaseEntity {

    /**
     * 教师用户ID（关联 sys_user.id）
     */
    @TableField("user_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 学科领域ID（关联 subject.id）
     */
    @TableField("subject_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long subjectId;

}
