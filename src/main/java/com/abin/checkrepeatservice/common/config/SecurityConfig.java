package com.abin.checkrepeatservice.common.config;

import com.abin.checkrepeatservice.common.jwt.JwtAuthenticationEntryPoint;
import com.abin.checkrepeatservice.common.jwt.JwtAuthenticationFilter;
import com.abin.checkrepeatservice.common.xss.XssFilter;
import com.abin.checkrepeatservice.user.service.Impl.UserDetailsServiceImpl;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
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
import org.springframework.security.web.context.SecurityContextHolderFilter;

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

    // 安全修复（S9）：全局 XSS 输入过滤，注册在 JWT 过滤器之前（对匿名接口同样生效）
    @Autowired
    private XssFilter xssFilter;

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
     * 角色层级（P1 职责分离）：声明式表达「超级管理员 ⊇ 管理员」。
     *
     * 生效范围（Spring Security 6.3+，本项目 Boot 3.5.7 / Security 6.5.x）：
     *   - authorizeHttpRequests 的 URL 规则（/api/admin/** 等的 hasAuthority("ADMIN")）
     *   - 方法级 @PreAuthorize 的 hasAuthority(...)（PrePostMethodSecurityConfiguration 自动注入）
     * 层级单向，ADMIN 不会反过来获得 SUPER_ADMIN，职责分离的
     * hasAuthority("SUPER_ADMIN") 检查点只认真实持有者。
     *
     * ⚠️ 双保险约定：本 bean 与 UserDetailsServiceImpl 发放侧的超管补授 ADMIN
     * 【必须同时存在】。这里曾单独作为唯一来源，线上因部署 jar 的构建/依赖版本
     * 错配（打包的 Security < 6.3 时 hasAuthority 不接层级）导致超管全站 403 ——
     * 层级生效与否依赖运行时框架行为，而发放侧补授不依赖任何框架接线，
     * 属于「永远有效」的兜底。二者语义一致，冗余是刻意的纵深防御。
     *
     * static：层级是纯静态规则，提前实例化不依赖任何业务 bean，避免容器
     * 过早初始化 SecurityConfig 引发警告或意外的 bean 装配顺序。
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("SUPER_ADMIN").implies("ADMIN")
                // 刻意不连坐 ADMIN -> TEACHER：保持「管理端 ≠ 教师端」的既有语义
                .build();
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
                        // 结果：任何「已登录但权限不足」都返回 401 而非 403，前端再把 401 当成登录过期
                        // 直接清 token 踢下线。放行 /error 后，403 才能原样到达调用方。
                        // 注：/ws/info 与 /actuator/health 那两次 401 也是同一类问题的不同表现。
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/error").permitAll()
                        // 放行公开接口
                        // /ws/** 是 SockJS 握手入口，握手走 HTTP 请求，必须在过滤器链上放行，
                        // STOMP CONNECT 帧仍由 WebSocketChannelInterceptor 校验 JWT，无有效 token 照样拒绝。
                        // /api/file/**（论文文件上传/下载/预览）与 /api/papers/**（学生论文/查重数据）
                        // /api/minio/** 收归 ADMIN（配合 MinioController 类级 @PreAuthorize 双保险）。
                        .requestMatchers("/api/auth/**", "/api/avatar/**", "/ws/**").permitAll()
                        .requestMatchers("/api/minio/**").hasAuthority("ADMIN")
                        // kkFileView 服务端回调专用：匿名仅可访问签名取文件端点，
                        // 必须在 /api/file/** 的 authenticated 规则之前
                        .requestMatchers("/api/file/preview-fetch/**").permitAll()
                        // 报告预览的同类回调端点：报告文件在宿主文件系统上（非 MinIO），
                        // 同样由后端签发绑定 reportId 的 HMAC 令牌（键名 report:<id>，
                        // 与文件令牌命名空间隔离），仅 kkFileView 服务端回调可访问
                        .requestMatchers("/api/file/preview-fetch-report/**").permitAll()
                        .requestMatchers("/api/file/**", "/api/papers/**").authenticated()
                        // 这里显式按角色收口，Service 层再做数据归属二次校验。
                        .requestMatchers("/api/check/**").hasAuthority("STUDENT")
                        .requestMatchers("/api/advisor/**").hasAuthority("ADMIN")
                        .requestMatchers("/api/assignment/**").hasAuthority("TEACHER")
                        // P5 报告验真：对齐行业报告验真惯例，凭报告编号匿名验证报告真伪，
                        // 只回元数据（标题打码/指标/时间），不回报告文件与正文；接口内有 IP 限流
                        .requestMatchers("/api/report/verify/**").permitAll()
                        // 系统状态查询（维护模式开关+公告）：前端未登录也要能拉取，
                        // 用于登录页/刷新时判断是否显示维护面板；且维护拦截器将其列入白名单
                        .requestMatchers("/api/system/status").permitAll()
                        // 容器 healthcheck 与 deploy.sh 都用它做探活，必须匿名可访问
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Prometheus 抓取端点：限管理员。
                        // 对匿名调用方是实打实的信息泄露，也与本项目其余部分的 ADMIN-only 姿态不一致。
                        // 更标准的做法是另开 management.server.port 绑内网 + 网络白名单。
                        .requestMatchers("/actuator/prometheus").hasAuthority("ADMIN")
                        // 任意学生登录即可查看 JVM/HTTP 指标；health 已在前面放行，其余 actuator 端点全部收归 ADMIN
                        .requestMatchers("/actuator/**").hasAuthority("ADMIN")
                        // 学生接口：仅学生角色可访问
                        .requestMatchers("/api/student/**").hasAuthority("STUDENT")
                        // 根路径按 HTTP 方法区分：POST 是「新增学生」，GET 仍是教师查学生列表，
                        // 不能把整个 /api/teacher/students 都收归 ADMIN。
                        .requestMatchers(HttpMethod.POST, "/api/teacher/students").hasAuthority("ADMIN")
                        // 教师端只保留「查」，删除/批量删除收归 ADMIN（方法级再做双保险）。
                        .requestMatchers(HttpMethod.DELETE, "/api/teacher/students/**").hasAuthority("ADMIN")
                        .requestMatchers("/api/teacher/students/add",
                                "/api/teacher/students/import",
                                "/api/teacher/students/import-template",
                                "/api/teacher/students/batch-assign-advisor").hasAuthority("ADMIN")
                        // 教师接口：仅教师角色可访问
                        .requestMatchers("/api/teacher/**").hasAuthority("TEACHER")
                        // 管理员接口：仅管理员角色可访问
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                        // 管理端通知管理接口（不在 /api/admin/** 下，需单独列出）
                        // 这条规则不能省、也不能只靠 NoticeController 上的 @PreAuthorize：
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
                // XSS 输入过滤放在 Security 链前段，保证登录/注册等匿名入口也被覆盖。
                // 注意：addFilterBefore/After 的锚点必须是 Spring Security 内置过滤器——
                // 自定义过滤器（如 JwtAuthenticationFilter）未在 FilterOrderRegistration 注册
                // order，作为锚点会在启动时抛 IllegalArgumentException:
                .addFilterBefore(xssFilter, SecurityContextHolderFilter.class)
                // 在UsernamePasswordAuthenticationFilter之前添加JWT认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
