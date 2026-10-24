package com.abin.checkrepeatservice.user.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.dto.ForgotPasswordReq;
import com.abin.checkrepeatservice.pojo.dto.LoginReq;
import com.abin.checkrepeatservice.pojo.dto.RefreshTokenReq;
import com.abin.checkrepeatservice.pojo.dto.RegisterReq;
import com.abin.checkrepeatservice.pojo.dto.SendPasswordResetCodeReq;
import com.abin.checkrepeatservice.user.dto.UpdateUserInfoReq;
import com.abin.checkrepeatservice.user.service.AuthService;
import com.abin.checkrepeatservice.user.vo.LoginVO;
import com.abin.checkrepeatservice.user.vo.RefreshTokenVO;
import jakarta.annotation.Resource;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器（仅负责请求接收与响应，无业务逻辑）
 */
@Slf4j
@RestController
@RequestMapping("/api/auth") // 接口前缀，便于版本管理
public class AuthController {

    // 仅依赖Service接口，不依赖具体实现（符合依赖倒置原则）
    @Resource
    private AuthService authService;

    /**
     * 用户注册接口（请求→Service→响应）
     */
    @PostMapping("/register")
    @OperationLog(type = "user_register", description = "用户注册")
    public Result<String> register(@Valid @RequestBody RegisterReq registerReq) {
        log.info("接收用户注册请求：用户名={}，角色ID={}", registerReq.getUsername(), registerReq.getRoleId());
        return authService.register(registerReq);
    }

    /**
     * 用户登录接口（请求→Service→响应）
     */
    @PostMapping("/login")
    @OperationLog(type = "user_login", description = "用户登录", recordParams = false)
    public Result<LoginVO> login(@Valid @RequestBody LoginReq loginReq) {
        log.info("接收用户登录请求：用户名={}", loginReq.getUsername());
        return authService.login(loginReq);
    }

    /**
     * 令牌刷新接口（请求→Service→响应）
     */
    @PostMapping("/refresh-token")
    public Result<RefreshTokenVO> refreshToken(@Valid @RequestBody RefreshTokenReq refreshTokenReq) {
        String oldToken = refreshTokenReq.getOldToken();
        // 日志脱敏（阶段 5）：只保留前 8 位，禁止完整 token 落日志
        String maskedToken = (oldToken == null || oldToken.length() <= 8)
                ? "***" : oldToken.substring(0, 8) + "***";
        log.info("接收令牌刷新请求：旧令牌={}", maskedToken);
        return authService.refreshToken(refreshTokenReq);
    }

    /**
     * 用户退出登录接口（请求→Service→响应）
     */
    @PostMapping("/logout")
    @OperationLog(type = "user_logout", description = "用户退出登录")
    public Result<String> logout() {
        log.info("接收用户退出登录请求");
        return authService.logout();
    }
    /**
     * 发送密码重置验证码接口（找回密码第一步：用户名 + 邮箱 → 邮箱收码）
     */
    @PostMapping("/send-password-reset-code")
    public Result<String> sendPasswordResetCode(@Valid @RequestBody SendPasswordResetCodeReq req) {
        log.info("接收发送密码重置验证码请求：用户名={}", req.getUsername());
        return authService.sendPasswordResetCode(req);
    }

    /**
     * 忘记密码接口（通过用户名和邮箱验证身份后重置密码）
     */
    @PostMapping("/forgot-password")
    public Result<String> forgotPassword(@Valid @RequestBody ForgotPasswordReq forgotPasswordReq) {
        log.info("接收忘记密码请求：用户名={}", forgotPasswordReq.getUsername());
        return authService.forgotPassword(forgotPasswordReq);
    }

}