package com.abin.checkrepeatservice.admin.vo;

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
}