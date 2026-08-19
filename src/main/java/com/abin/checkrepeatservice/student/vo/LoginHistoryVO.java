package com.abin.checkrepeatservice.student.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginHistoryVO {

    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 用户名
     */
    private String username;
    /**
     * 登录时间
     */
    private LocalDateTime loginTime;

    /**
     * 登录IP
     */
    private String loginIp;

    /**
     * 登录位置
     */
    private String loginLocation;

    /**
     * 登录浏览器
     */
    private String loginDevice;

    /**
     * 登录状态
     */
    private Integer loginResult;

    /**
     * 登录失败原因
     */
    private String failReason;

}
