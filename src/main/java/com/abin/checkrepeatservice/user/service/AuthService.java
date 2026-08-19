package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.dto.ForgotPasswordReq;
import com.abin.checkrepeatservice.pojo.dto.LoginReq;
import com.abin.checkrepeatservice.pojo.dto.RefreshTokenReq;
import com.abin.checkrepeatservice.pojo.dto.RegisterReq;
import com.abin.checkrepeatservice.user.dto.UpdateUserInfoReq;
import com.abin.checkrepeatservice.user.vo.LoginVO;
import com.abin.checkrepeatservice.user.vo.RefreshTokenVO;

/**
 * 认证服务接口（定义登录、注册、令牌刷新核心能力）
 */
public interface AuthService {

    /**
     * 用户注册
     * @param registerReq 注册请求参数（用户名、密码、角色ID等）
     * @return 注册结果（成功/失败提示）
     */
    Result<String> register(RegisterReq registerReq);

    /**
     * 用户登录
     * @param loginReq 登录请求参数（用户名、密码）
     * @return 登录结果（含JWT令牌、用户基本信息、角色信息）
     */
    Result<LoginVO> login(LoginReq loginReq);

    /**
     * 令牌刷新
     * @param refreshTokenReq 刷新请求参数（旧令牌）
     * @return 刷新结果（新JWT令牌、新令牌过期时间）
     */
    Result<RefreshTokenVO> refreshToken(RefreshTokenReq refreshTokenReq);

    /**
     * 用户退出登录
     * @return 退出结果（成功提示）
     */
    Result<String> logout();

    /**
     * 忘记密码（通过用户名和邮箱验证身份后重置密码）
     */
    Result<String> forgotPassword(ForgotPasswordReq forgotPasswordReq);


}
