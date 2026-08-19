package com.abin.checkrepeatservice.pojo.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 令牌刷新请求实体：封装旧令牌参数
 */
@Data
public class RefreshTokenReq {
    /**
     * 待刷新的旧JWT令牌
     */
    @NotBlank(message = "刷新令牌不能为空")
    private String oldToken;
}
