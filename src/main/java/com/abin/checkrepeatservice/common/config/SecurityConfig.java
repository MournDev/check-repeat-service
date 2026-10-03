package com.abin.checkrepeatservice.common.config;

import com.abin.checkrepeatservice.common.jwt.JwtAuthenticationEntryPoint;
import com.abin.checkrepeatservice.common.jwt.JwtAuthenticationFilter;
import com.abin.checkrepeatservice.user.service.Impl.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security配置：权限控制、JWT集成、安全过滤
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 启用方法级别的权限控制
public class SecurityConfig {

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 密码编码器（BCrypt加密）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证提供者：关联用户详情服务与密码编码器
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * 认证管理器：处理认证请求
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * 安全过滤链：配置URL权限、会话策略、异常处理
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 关闭CSRF（前后端分离项目无需CSRF保护）
                .csrf(csrf -> csrf.disable())
                // 配置未认证请求的异常处理器
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedHandler))
                // 配置会话策略：无状态（JWT认证无需会话）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置URL权限规则
                .authorizeHttpRequests(auth -> auth
                        // 放行公开接口
                        // /ws/** 是 SockJS 握手入口，握手走 HTTP 请求，必须在过滤器链上放行，
                        // 否则 /ws/info 会直接 401。握手放行不等于免鉴权：
                        // STOMP CONNECT 帧仍由 WebSocketChannelInterceptor 校验 JWT，无有效 token 照样拒绝。
                        .requestMatchers("/api/auth/**", "/api/avatar/**", "/api/papers/**", "/api/file/**", "/api/minio/**", "/ws/**").permitAll()
                        // 容器 healthcheck 与 deploy.sh 都用它做探活，必须匿名可访问
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // 学生接口：仅学生角色可访问
                        .requestMatchers("/api/student/**").hasAuthority("STUDENT")
                        // 教师接口：仅教师角色可访问
                        .requestMatchers("/api/teacher/**").hasAuthority("TEACHER")
                        // 管理员接口：仅管理员角色可访问
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                        // 管理端通知管理接口（不在 /api/admin/** 下，需单独列出）
                        // 这条规则不能省、也不能只靠 NoticeController 上的 @PreAuthorize：
                        // GlobalExceptionHandler 没有 AccessDeniedException 处理器，方法级安全
                        // 拒绝会被 Exception 兜底成 HTTP 500；过滤器链上的规则才产生正确的 403
                        .requestMatchers("/api/notices/**").hasAuthority("ADMIN")
                        // 知识库管理端（同样不在 /api/admin/** 下）。只圈 admin 子路径：
                        // /api/knowledge/** 的其余部分（categories/articles/search）是学生端要用的，
                        // 圈大了会把学生挡在帮助中心外面。理由同 /api/notices/**
                        .requestMatchers("/api/knowledge/admin/**").hasAuthority("ADMIN")
                        // 消息模板管理端（同上，不在 /api/admin/** 下）
                        .requestMatchers("/api/message-templates/**").hasAuthority("ADMIN")
                        // 其他接口需认证
                        .anyRequest().authenticated())
                // 注册认证提供者
                .authenticationProvider(authenticationProvider())
                // 在UsernamePasswordAuthenticationFilter之前添加JWT认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
