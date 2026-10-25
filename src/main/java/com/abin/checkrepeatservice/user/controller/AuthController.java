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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "认证模块", description = "登录/注册/刷新Token")
public class AuthController {

    // 仅依赖Service接口，不依赖具体实现（符合依赖倒置原则）
    @Resource
    private AuthService authService;

    /**
     * 用户注册接口（请求→Service→响应）
     */
    @PostMapping("/register")
    @OperationLog(type = "user_register", description = "用户注册")
    @Operation(summary = "用户注册", description = "根据角色ID创建学生/教师/管理员账号")
    public Result<String> register(
            @Parameter(description = "注册请求体：用户名、密码、邮箱、角色ID 等", required = true)
            @Valid @RequestBody RegisterReq registerReq) {
        log.info("接收用户注册请求：用户名={}，角色ID={}", registerReq.getUsername(), registerReq.getRoleId());
        return authService.register(registerReq);
    }

    /**
     * 用户登录接口（请求→Service→响应）
     */
    @PostMapping("/login")
    @OperationLog(type = "user_login", description = "用户登录", recordParams = false)
    @Operation(summary = "用户登录", description = "传入用户名密码获取JWT")
    public Result<LoginVO> login(
            @Parameter(description = "登录请求体：username 登录账号，password 明文密码", required = true)
            @Valid @RequestBody LoginReq loginReq) {
        log.info("接收用户登录请求：用户名={}", loginReq.getUsername());
        return authService.login(loginReq);
    }

    /**
     * 令牌刷新接口（请求→Service→响应）
     */
    @PostMapping("/refresh-token")
    @Operation(summary = "刷新令牌", description = "使用旧 token 换取新的访问令牌")
    public Result<RefreshTokenVO> refreshToken(
            @Parameter(description = "刷新令牌请求体：oldToken 为当前仍有效的旧令牌", required = true)
            @Valid @RequestBody RefreshTokenReq refreshTokenReq) {
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
    @Operation(summary = "退出登录", description = "将当前 token 加入吊销黑名单")
    public Result<String> logout() {
        log.info("接收用户退出登录请求");
        return authService.logout();
    }
    /**
     * 发送密码重置验证码接口（找回密码第一步：用户名 + 邮箱 → 邮箱收码）
     */
    @PostMapping("/send-password-reset-code")
    @Operation(summary = "发送密码重置验证码", description = "校验用户名与邮箱匹配后，向邮箱发送验证码")
    public Result<String> sendPasswordResetCode(
            @Parameter(description = "发送验证码请求体：username 用户名，email 绑定邮箱", required = true)
            @Valid @RequestBody SendPasswordResetCodeReq req) {
        log.info("接收发送密码重置验证码请求：用户名={}", req.getUsername());
        return authService.sendPasswordResetCode(req);
    }

    /**
     * 忘记密码接口（通过用户名和邮箱验证身份后重置密码）
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "忘记密码", description = "通过用户名、邮箱与验证码重置密码")
    public Result<String> forgotPassword(
            @Parameter(description = "重置密码请求体：用户名、邮箱、验证码与新密码", required = true)
            @Valid @RequestBody ForgotPasswordReq forgotPasswordReq) {
        log.info("接收忘记密码请求：用户名={}", forgotPasswordReq.getUsername());
        return authService.forgotPassword(forgotPasswordReq);
    }

}