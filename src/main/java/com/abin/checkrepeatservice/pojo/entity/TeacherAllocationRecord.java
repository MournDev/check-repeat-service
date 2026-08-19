package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 教师分配记录实体类
 * 记录论文指导老师的分配历史和相关信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("teacher_allocation_record")
public class TeacherAllocationRecord extends BaseEntity {

    /**
     * 论文ID（关联paper_info.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("paper_id")
    private Long paperId;

    /**
     * 学生ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("student_id")
    private Long studentId;

    /**
     * 教师ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("teacher_id")
    private Long teacherId;

    /**
     * 分配类型（AUTO-自动分配, MANUAL-手动分配, ADJUST-调整分配）
     */
    @TableField("allocation_type")
    private String allocationType;

    /**
     * 分配原因
     */
    @TableField("allocation_reason")
    private String allocationReason;

    /**
     * 分配时间
     */
    @TableField("allocation_time")
    private LocalDateTime allocationTime;

    /**
     * 操作人ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("operator_id")
    private Long operatorId;

    /**
     * 分配状态（active-有效, revoked-已撤销, expired-已过期）
     */
    @TableField("allocation_status")
    private String allocationStatus;

    // 冗余字段 - 便于查询显示
    /**
     * 学生姓名
     */
    @TableField(exist = false)
    private String studentName;

    /**
     * 教师姓名
     */
    @TableField(exist = false)
    private String teacherName;

    /**
     * 论文标题
     */
    @TableField(exist = false)
    private String paperTitle;

    /**
     * 操作人姓名
     */
    @TableField(exist = false)
    private String operatorName;
}