package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审核记录实体：对应review_record表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_record")
public class ReviewRecord extends BaseEntity {
    /**
     * 对应的论文ID（关联paper_info.id）
     */
    private Long paperId;

    /**
     * 对应的查重任务ID（关联check_task.id，基于该任务结果审核）
     */
    private Long taskId;

    /**
     * 执行审核的教师ID（关联sys_user.id）
     */
    private Long teacherId;

    /**
     * 审核状态（3-审核通过，4-审核不通过）
     */
    private Integer reviewStatus;

    /**
     * 审核意见（富文本，可为空）
     */
    private String reviewOpinion;

    /**
     * 建议修改点（JSON 格式存储具体修改建议，可为空）
     * 示例：[{"type":"format","desc":"格式不规范"},{"type":"content","desc":"第三章内容不够充实"}]
     */
    @TableField("suggested_modifications")
    private String suggestedModifications;

    /**
     * 审核附件路径（如教师添加的修改建议文档，可为空）
     */
    private String reviewAttach;

    /**
     * 审核时间
     */
    private LocalDateTime reviewTime;
    
    /**
     * 论文标题（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String paperTitle;
    
    /**
     * 学生姓名（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String studentName;
    
    /**
     * 学生学号（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String studentNo;
    
    /**
     * 教师姓名（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String teacherName;
    
    /**
     * 审核状态描述（冗余字段，便于显示）
     */
    @TableField(exist = false)
    private String reviewStatusDesc;
    
    /**
     * 查重重复率（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private BigDecimal checkRate;
    
    /**
     * 论文提交时间（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private LocalDateTime submitTime;
}
