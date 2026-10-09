package com.abin.checkrepeatservice.teacher.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生分组视图（教师端分组卡片）
 */
@Data
public class StudentGroupVO {

    /**
     * 分组ID（序列化为字符串避免JS精度丢失）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 分组名称
     */
    private String name;

    /**
     * 分组描述
     */
    private String description;

    /**
     * 学生人数
     */
    private Integer studentCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 组内学生列表
     */
    private List<GroupStudentVO> students;
}
