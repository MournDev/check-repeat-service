package com.abin.checkrepeatservice.teacher.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分组内学生视图（也用于「未分组学生」列表）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupStudentVO {

    /**
     * 学生用户ID（关联sys_user.id，序列化为字符串避免JS精度丢失）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 学生姓名（sys_user.real_name）
     */
    private String name;

    /**
     * 学号（sys_user.username）
     */
    private String studentId;

    /**
     * 学院名称
     */
    private String collegeName;

    /**
     * 专业名称（sys_user.major）
     */
    private String majorName;
}
