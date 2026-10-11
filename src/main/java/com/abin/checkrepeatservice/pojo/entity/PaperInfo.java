package com.abin.checkrepeatservice.pojo.entity;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 论文信息实体：对应paper_info表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("paper_info") // 绑定数据库表名
public class PaperInfo extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 论文标题
     */
    @TableField("paper_title")
    @NotBlank(message = "论文标题不能为空")
    @Size(max = 200, message = "论文标题长度不能超过200字符")
    private String paperTitle;

    /**
     * 学生ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("student_id")
    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    /**
     * 作者信息
     */
    @TableField("author")
    private String author;

    /**
     * 学院ID（关联college.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("college_id")
    private Long collegeId;

    /**
     * 专业ID（关联major.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("major_id")
    private Long majorId;

    /**
     * 导师ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("teacher_id")
    private Long teacherId;

    /**
     * 导师姓名
     */
    @TableField("teacher_name")
    private String teacherName;

    /**
     * 分配类型
     */
    @TableField("allocation_type")
    private String allocationType;

    /**
     * 分配状态
     */
    @TableField("allocation_status")
    private String allocationStatus;

    /**
     * 分配时间
     */
    @TableField("allocation_time")
    private LocalDateTime allocationTime;

    /**
     * 确认时间
     */
    @TableField("confirm_time")
    private LocalDateTime confirmTime;

    /**
     * 论文类型
     */
    @TableField("paper_type")
    @NotBlank(message = "论文类型不能为空")
    private String paperType;

    /**
     * 论文摘要
     */
    @TableField("paper_abstract")
    @NotBlank(message = "论文摘要不能为空")
    @Size(max = 2000, message = "论文摘要长度不能超过2000字符")
    private String paperAbstract;

    /**
     * 文件 ID
     */
    @TableField("file_id")
    private Long fileId;

    /**
     * 文件MD5值
     */
    @TableField("file_md5")
    private String fileMd5;

    /**
     * 字数统计
     */
    @TableField("word_count")
    private Integer wordCount;

    /**
     * 论文状态
     */
    @TableField("paper_status")
    private String paperStatus;

    /**
     * 当前多级初审步骤（论文全生命周期 P2，列见 db/补论文全生命周期P2.sql）：
     * 0-未进入工作流（导师初审中/已完成/未启用工作流）；
     * 1..N-初审工作流第 N 步审批中（步骤定义在 review_workflow.steps，1-based）。
     * 仅在 paper_status=auditing 且启用工作流时有意义。
     */
    @TableField("workflow_step")
    private Integer workflowStep;

    /**
     * 相似度比率
     */
    @TableField("similarity_rate")
    private BigDecimal similarityRate;

    /**
     * 查重结果
     */
    @TableField("check_result")
    private String checkResult;
    
    /**
     * 查重引擎类型
     */
    @TableField("check_engine_type")
    private String checkEngineType;
    
    /**
     * 查重是否完成
     */
    @TableField("check_completed")
    private Integer checkCompleted;
    
    /**
     * 查重来源
     */
    @TableField("check_source")
    private String checkSource;

    /**
     * 提交时间
     */
    @TableField("submit_time")
    private LocalDateTime submitTime;

    /**
     * 查重时间
     */
    @TableField("check_time")
    private LocalDateTime checkTime;

    /**
     * 文件路径
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 最终成绩
     */
    @TableField("final_score")
    private BigDecimal finalScore;

    /**
     * 学科领域编码
     *
     * 与 subjectId 是一对冗余列（沿用 collegeId + collegeName 的既有模式）：
     * 这一列是**对外稳定标识**，100 个 code 全在 subject 表里；
     * AcademicIntegrityServiceImpl 把它当 subjectArea 拿去 contains 匹配资源分类，
     * 所以值域必须保持"编码"形态（如 ENG-CS），改造时一个字都不能动。
     */
    @TableField("subject_code")
    private String subjectCode;

    /**
     * 学科领域ID（关联subject.id；一级门类、二级学科都在 subject 表里，两者都可选）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("subject_id")
    private Long subjectId;

    /**
     * 学科领域名称
     */
    @TableField(exist = false)
    private String subjectName;
    
    /**
     * 学院名称
     */
    @TableField(exist = false)
    private String collegeName;
    
    /**
     * 专业名称
     */
    @TableField(exist = false)
    private String majorName;
    
    /**
     * 学生真实姓名（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String studentName;
    
    /**
     * 学生学号（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String studentUsername;
    
    /**
     * 学生年级（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String studentGrade;
    
    /**
     * 学生专业（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String studentMajor;
    
    /**
     * 学生学院（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String studentCollege;
    
    /**
     * 教师真实姓名（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String teacherRealName;
    
    /**
     * 论文正文在MinIO中的存储路径
     */
    @TableField(exist = false)
    private String contentPath;
    
    /**
     * IK分词后文本在MinIO中的存储路径
     */
    @TableField(exist = false)
    private String segmentedPath;
}
