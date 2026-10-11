package com.abin.checkrepeatservice.common.interceptor;

import com.abin.checkrepeatservice.common.maintenance.MaintenanceStateHolder;
import com.abin.checkrepeatservice.common.utils.AuthInfoUtils;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 维护模式拦截器 —— 维护期间对非管理员用户的 /api/** 请求返回 503
 *
 * <p><b>注册</b>：WebMvcConfig（addPathPatterns("/api/**")，
 * excludePathPatterns("/api/auth/**", "/api/system/status")）。/ws/** 不在 /api/**
 * 下天然放行——在线用户能实时收到「维护结束」推送。</p>
 *
 * <p><b>状态来源</b>：{@link MaintenanceStateHolder} 内存标志（volatile），
 * 仅在维护开关接口落库后刷新，<b>禁止每请求查库</b>。</p>
 *
 * <p><b>放行规则</b>：当前用户 authorities 含 ADMIN（SUPER_ADMIN 账号发放时
 * 已补授 ADMIN，见 UserDetailsServiceImpl 的双保险约定）即放行；
 * 未登录/普通用户一律 503。</p>
 *
 * <p><b>前端约定</b>：request.js 收到 503 + errorCode=SYSTEM_MAINTENANCE 时更新
 * 全局维护 store，显示红色横幅与维护面板，不再弹通用错误提示。</p>
 */
@Component
@Slf4j
public class MaintenanceInterceptor implements HandlerInterceptor {

    /** 前端 503 分支的判定错误码（契约固定值，勿改） */
    public static final String ERROR_CODE = "SYSTEM_MAINTENANCE";

    private static final String DEFAULT_NOTICE = "系统维护中，请稍后再试";

    @Resource
    private MaintenanceStateHolder maintenanceStateHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!maintenanceStateHolder.isMaintenance()) {
            return true;
        }

        // 管理员放行（运维需要在维护期间继续操作）
        if (isCurrentUserAdmin()) {
            return true;
        }

        // 503 + JSON 错误体（前端 request.js 按 errorCode 识别）
        String notice = maintenanceStateHolder.getMaintenanceNotice();
        String message = (notice == null || notice.isBlank()) ? DEFAULT_NOTICE : notice;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 503);
        body.put("errorCode", ERROR_CODE);
        body.put("message", message);
        body.put("data", null);
        body.put("success", false);

        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8));
        return false;
    }

    /**
     * 当前登录用户是否管理员（ADMIN 或 SUPER_ADMIN）
     */
    private boolean isCurrentUserAdmin() {
        try {
            UserDetails userDetails = AuthInfoUtils.getCurrentUserDetails();
            if (userDetails == null) {
                return false;
            }
            return userDetails.getAuthorities().stream()
                    .anyMatch(a -> "ADMIN".equals(a.getAuthority())
                            || "SUPER_ADMIN".equals(a.getAuthority()));
        } catch (Exception e) {
            // 取不到用户身份一律视为非管理员
            return false;
        }
    }
}
