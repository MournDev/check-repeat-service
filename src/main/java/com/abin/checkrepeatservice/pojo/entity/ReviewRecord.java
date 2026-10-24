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
     * 审核来源：TEACHER-导师审核 / ADMIN_PROXY-管理员代审。
     * 由服务端按当前登录角色裁决写入，不接受客户端传值；历史数据默认 TEACHER。
     */
    private String auditSource;

    /**
     * 代审原因（仅 audit_source=ADMIN_PROXY 有值，服务端必填校验）
     */
    private String proxyReason;

    /**
     * 实际出结论人ID（关联 sys_user.id），用于区分责任主体
     */
    private Long auditorId;

    /**
     * 实际出结论人角色编码（TEACHER/ADMIN/SUPER_ADMIN）
     */
    private String auditorRole;

    /**
     * 关联的查重报告签核记录ID（paper_check_signoff.id）；管理员代审为空，
     * 可据此反查该次结论是否绕过了导师签核。
     */
    private Long signoffId;
    
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
