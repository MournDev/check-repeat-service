package com.abin.checkrepeatservice.common.interceptor;

import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.user.service.SysUserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;


/**
 * 登录拦截器：解析Token，获取当前登录用户ID
 *
 * ⚠️ 注意：本类目前【没有被注册】，也没有任何 WebMvcConfigurer 引用它
 * （全仓搜索 addInterceptors 无命中），属于历史遗留 —— 实际认证由
 * JwtAuthenticationFilter + Spring Security 过滤器链完成，@PreAuthorize 生效。
 * 这里保留是为了不与历史代码冲突；若要启用，需在配置类里 addInterceptors 注册。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtils jwtUtils; // 自定义Jwt工具类，用于解析Token
    @Resource
    private SysUserService sysUserService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头获取Token（整头取出，形如 "Bearer eyJ..."）
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "未登录：请先获取Token");
            return false;
        }

        // 2. 解析Token中的用户ID
        //    前缀剥离在 JwtUtils 内部统一完成，这里传整头即可（原先没剥前缀，必然报"Token格式错误"）
        Long userId;
        try {
            userId = jwtUtils.getUserIdFromToken(token);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token无效：" + e.getMessage());
            return false;
        }

        // 3. 校验用户是否存在且状态正常
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getIsDeleted() == 1 || user.getStatus() == 0) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "用户不存在或已禁用");
            return false;
        }

        // 4. 将用户ID放入RequestAttribute，供Controller使用
        request.setAttribute("loginUserId", userId);
        return true;
    }
}
