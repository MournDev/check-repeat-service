package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user") // 关键注解：指定关联的数据库表为sys_user
public class SysUser extends BaseEntity {

    /**
     * 头像（图片URL）
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 登录账号（唯一，学生用学号、教师用工号、管理员自定义）
     */
    @TableField("username")
    @NotBlank(message = "登录账号不能为空")
    @Size(max = 50, message = "登录账号长度不能超过50字符")
    private String username;

    /**
     * 密码（BCrypt加密存储）
     */
    @TableField("password")
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 真实姓名
     */
    @TableField("real_name")
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名长度不能超过50字符")
    private String realName;

    /**
     * 职称（仅教师填写，学生/管理员可为空）
     * 适用用户类型：TEACHER
     * 示例值：教授、副教授、讲师等
     */
    @TableField("professional_title")
    private String professionalTitle;
    /**
     * 角色ID（关联sys_role.id）
     */
    @TableField("role_id")
    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    /**
     * 学院ID（关联sys_college.id）
     */
    @TableField("college_id")
    private Long collegeId;

    /**
     * 学院名称
     */
    @TableField("college_name")
    private String collegeName;

    /**
     * 专业ID（关联sys_major.id）
     */
    @TableField("major_id")
    private Long majorId;

    /**
     * 科研方向（仅教师填写，学生/管理员可为空）
     * 适用用户类型：TEACHER
     * 示例值：人工智能、数据挖掘、网络安全等
     */
    @TableField("research_direction")
    private String researchDirection;

    /**
     * 当前指导教师数量（仅教师填写，学生/管理员可为空）
     * 适用用户类型：TEACHER
     * 用于限制教师指导学生数量
     */
    @TableField("current_advisor_count")
    private Integer currentAdvisorCount;
    /**
     * 年级（仅学生填写，教师/管理员可为空）
     * 适用用户类型：STUDENT
     * 示例值：2021级、2022级等
     */
    @TableField("grade")
    private String grade;

    /**
     * 专业（仅学生/教师填写，管理员可为空）
     * 适用用户类型：STUDENT, TEACHER
     * 学生的专业名称或教师所在专业
     */
    @TableField("major")
    private String major;

    /**
     * 班级（仅学生填写，教师/管理员可为空）
     * 适用用户类型：STUDENT
     * 示例值：计算机科学与技术1班
     */
    @TableField("class_name")
    private String className;

    /**
     * 邮箱（用于密码重置、消息通知，唯一）
     */
    @TableField("email")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100字符")
    private String email;

    /**
     * 邮箱验证状态（0-未验证，1-已验证）
     */
    @TableField("email_verified")
    private Integer emailVerified;

    /**
     * 手机号（可为空）
     */
    @TableField("phone")
    private String phone;

    /**
     * 个人介绍
     */
    @TableField("introduce")
    private String introduce;

    /**
     * 账号状态（0-禁用，1-正常）
     */
    @TableField("status")
    private Integer status;

    /**
     * 最后登录时间（可为空）
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 用户类型（ADMIN-管理员，STUDENT-学生，TEACHER-教师）
     */
    @TableField("user_type")
    private String userType;

    /**
     * 办公地点（仅教师填写，学生/管理员可为空）
     * 适用用户类型：TEACHER
     * 示例值：主楼A座301室
     */
    @TableField("office")
    private String office;

    /**
     * 办公时间（仅教师填写，学生/管理员可为空）
     */
    @TableField("office_hours")
    private String officeHours;

    /**
     * 最大审核数量（仅教师填写，学生/管理员可为空）
     */
    @TableField("max_review_count")
    private Integer maxReviewCount;

    /**
     * 审核期限（仅教师填写，学生/管理员可为空）
     */
    @TableField("review_deadline")
    private Integer reviewDeadline;

    /**
     * 通知开关-系统异常：0-关，1-开（管理员个人设置页）
     */
    @TableField("notify_system_error")
    private Integer notifySystemError;

    /**
     * 通知开关-安全警告：0-关，1-开（管理员个人设置页）
     */
    @TableField("notify_security_warning")
    private Integer notifySecurityWarning;

    /**
     * 通知开关-系统公告：0-关，1-开
     */
    @TableField("notify_system_announcement")
    private Integer notifySystemAnnouncement;

    /**
     * 通知开关-邮件通知：0-关，1-开
     */
    @TableField("notify_email")
    private Integer notifyEmail;

    /**
     * 个人偏好-操作日志保留天数（30~365）
     */
    @TableField("log_retention_days")
    private Integer logRetentionDays;

    /**
     * 个人偏好-数据备份频率：daily/weekly/monthly
     */
    @TableField("backup_frequency")
    private String backupFrequency;

    /**
     * 角色名称（冗余字段，便于显示）
     */
    @TableField(exist = false)
    private String roleName;
    
    /**
     * 学院名称（冗余字段，便于显示）
     */
    @TableField(exist = false)
    private String collegeDisplayName;
    
    /**
     * 专业名称（冗余字段，便于显示）
     */
    @TableField(exist = false)
    private String majorDisplayName;
}
