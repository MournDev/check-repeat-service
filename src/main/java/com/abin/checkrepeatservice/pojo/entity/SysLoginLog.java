package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统登录日志实体类
 * 记录用户的登录行为和安全相关信息
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {

    /**
     * 主键ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("user_id")
    private Long userId;

    /**
     * 用户名（登录账号）
     */
    @TableField("username")
    private String username;

    /**
     * 登录IP地址
     */
    @TableField("login_ip")
    private String loginIp;

    /**
     * 登录地点
     */
    @TableField("login_location")
    private String loginLocation;

    /**
     * 登录设备信息
     */
    @TableField("login_device")
    private String loginDevice;

    /**
     * 登录时间
     */
    @TableField("login_time")
    private LocalDateTime loginTime;

    /**
     * 登录结果（0-失败，1-成功）
     */
    @TableField("login_result")
    private Integer loginResult;

    /**
     * 失败原因
     */
    @TableField("fail_reason")
    private String failReason;

    /**
     * 软删除标记（0-未删除，1-已删除）
     */
    @TableField("is_deleted")
    private Integer isDeleted;

    // 冗余字段 - 便于查询显示
    /**
     * 用户真实姓名
     */
    @TableField(exist = false)
    private String realName;

    /**
     * 用户角色
     */
    @TableField(exist = false)
    private String roleName;
}