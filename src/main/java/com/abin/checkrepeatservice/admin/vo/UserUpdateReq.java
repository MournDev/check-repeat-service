package com.abin.checkrepeatservice.admin.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户更新请求VO
 */
@Data
public class UserUpdateReq {
    private String realName;
    private String email;
    private String phone;
    private String collegeName;
    private String major;
    private String grade;
    private String className;

    /**
     * 账号状态（可选）：null = 不修改状态（状态变更走 updateUserStatus 专用接口）。
     * 服务层对 null 跳过 setStatus，不会抹掉库里已有的状态。
     */
    private Integer status;

    // ---------- 个人设置页「通知设置 + 个人偏好」（均可选，null = 本次不修改） ----------

    /** 通知开关-系统异常 */
    private Boolean notifySystemError;

    /** 通知开关-安全警告 */
    private Boolean notifySecurityWarning;

    /** 通知开关-系统公告 */
    private Boolean notifySystemAnnouncement;

    /** 通知开关-邮件通知 */
    private Boolean notifyEmail;

    /** 个人偏好-操作日志保留天数 */
    @Min(value = 30, message = "操作日志保留天数不能小于30天")
    @Max(value = 365, message = "操作日志保留天数不能超过365天")
    private Integer logRetentionDays;

    /** 个人偏好-数据备份频率 */
    @Pattern(regexp = "^(daily|weekly|monthly)$", message = "备份频率仅支持 daily/weekly/monthly")
    private String backupFrequency;
}