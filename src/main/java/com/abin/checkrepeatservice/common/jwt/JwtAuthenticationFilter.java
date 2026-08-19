package com.abin.checkrepeatservice.common.jwt;

import com.abin.checkrepeatservice.user.service.Impl.UserDetailsServiceImpl;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器：拦截请求，解析令牌并设置认证信息到Security上下文
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private UserDetailsServiceImpl userDetailsService;

    // 从配置文件获取JWT请求头与前缀
    @Value("${jwt.token-header:Authorization}")
    private String tokenHeader;

    @Value("${jwt.token-prefix:Bearer }")
    private String tokenPrefix;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. 获取请求URI
            String requestUri = request.getRequestURI();

            // 2. 放行登录、注册和刷新令牌接口
            if (requestUri.startsWith("/api/auth/login")
                    || requestUri.startsWith("/api/auth/register")
                    || requestUri.startsWith("/api/auth/refresh-token")
                    || requestUri.startsWith("/api/auth/forgot-password")
            ) {
                // 直接放行，不进行JWT认证
                filterChain.doFilter(request, response);
                return;
            }
            // 1. 从请求头获取JWT令牌
            String jwt = parseJwt(request);
            // 2. 验证令牌有效性
            if (jwt != null && !jwtUtils.isTokenExpired(jwt)) {
                // 3. 从令牌中提取用户名
                String username = jwtUtils.extractUsername(jwt);
                // 4. 从用户详情服务获取用户信息
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                // 5. 构建认证令牌并设置到Security上下文
                // 登录成功后设置认证信息
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("无法设置用户认证信息: {}", e);
        }

        // 继续执行过滤链
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头解析JWT令牌（去除前缀）
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader(tokenHeader);
        if (StringUtils.hasText(headerAuth)) {
            // 移除Bearer前缀（如果存在）
            if (headerAuth.startsWith(tokenPrefix + " ")) {
                headerAuth = headerAuth.substring(tokenPrefix.length() + 1);
            }
            return headerAuth;
        }
        return null;
    }
}