package com.abin.checkrepeatservice.config;

import com.abin.checkrepeatservice.common.interceptor.MaintenanceInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置
 *
 * <p>此前项目没有 WebMvcConfigurer（LoginInterceptor 的注释可证），实际认证全靠
 * JwtAuthenticationFilter。本类只为维护模式拦截器提供注册点：</p>
 * <ul>
 *   <li>拦截 /api/**：维护期间普通用户业务请求收 503</li>
 *   <li>放行 /api/auth/**：否则维护中学生无法登录（契约验证清单第 5 条）</li>
 *   <li>放行 /api/system/status：公开的系统状态查询接口本身</li>
 *   <li>/ws/** 不在 /api/** 下，天然放行——在线用户能收到「维护结束」实时推送</li>
 * </ul>
 * <p>MVC 拦截器执行在 Security 过滤器链之后，SecurityContext 已就绪，
 * 拦截器里可以安全地取当前用户角色。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private MaintenanceInterceptor maintenanceInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(maintenanceInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/system/status");
    }
}
