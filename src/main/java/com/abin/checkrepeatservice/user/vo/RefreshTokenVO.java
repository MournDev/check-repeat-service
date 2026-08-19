package com.abin.checkrepeatservice.user.vo;


import lombok.Data;

/**
 * 令牌刷新响应 VO
 */
@Data
public class RefreshTokenVO {
    /** 新JWT令牌 */
    private String newToken;

    /** 新令牌过期时间（毫秒时间戳） */
    private Long expireTime;
}
