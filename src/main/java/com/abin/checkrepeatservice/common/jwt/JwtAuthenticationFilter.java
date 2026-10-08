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

    // 从配置文件获取JWT请求头与前缀（键与 JwtProperties 保持一致：spring.jwt.*）
    @Value("${spring.jwt.token-header:Authorization}")
    private String tokenHeader;

    @Value("${spring.jwt.token-prefix:Bearer}")
    private String tokenPrefix;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. 获取请求URI
            String requestUri = request.getRequestURI();

            // 2. 放行登录、注册、找回密码等未登录即可访问的接口
            if (requestUri.startsWith("/api/auth/login")
                    || requestUri.startsWith("/api/auth/register")
                    || requestUri.startsWith("/api/auth/refresh-token")
                    || requestUri.startsWith("/api/auth/forgot-password")
                    || requestUri.startsWith("/api/auth/send-password-reset-code")
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
            } else if (jwt != null) {
                // 令牌过期是"带着 token 却被当成匿名"的最常见原因，
                // 但走到这里会静默继续，最终只表现为一个 401，看不出是过期还是别的。
                // 注：logger 来自 GenericFilterBean（commons-logging），只有 warn(Object) /
                // warn(Object, Throwable)，没有 slf4j 的占位符重载，所以这里用字符串拼接。
                logger.warn("JWT 已过期，按匿名请求处理: " + request.getRequestURI());
            }
        } catch (Exception e) {
            // 带上堆栈：这里吞掉的异常（签名不匹配 / 用户不存在 / 令牌格式错）正是
            // "401 但查不出原因"的源头，只打 e.toString() 会把定位信息丢干净。
            logger.error("无法设置用户认证信息，按匿名请求处理", e);
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
            // 移除前缀（如果存在）
            // tokenPrefix 可配置：先去掉尾随空格再比较，兼容 "Bearer" 与 "Bearer " 两种写法
            String prefix = tokenPrefix == null ? "" : tokenPrefix.trim();
            if (!prefix.isEmpty() && headerAuth.startsWith(prefix)) {
                headerAuth = headerAuth.substring(prefix.length());
            }
            // 再去除可能残留的空格；未带前缀的裸 token 原样返回
            return headerAuth.trim();
        }
        return null;
    }
}