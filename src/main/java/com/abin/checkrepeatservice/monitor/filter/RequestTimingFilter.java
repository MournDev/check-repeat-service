package com.abin.checkrepeatservice.monitor.filter;

import com.abin.checkrepeatservice.monitor.service.ResponseTimeStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 逐请求耗时采集过滤器
 *
 * 放在 Servlet 顶层（而不做成 HandlerInterceptor）：
 *   1. 项目里没有任何 WebMvcConfigurer，用拦截器一样得新建一个；
 *   2. 拦截器观测不到 handler 映射之前就失败的请求（如未映射路径、鉴权直接拒绝）。
 *
 * 注册方式：只靠 @Component 由 Spring Boot 自动注册，【不要再加进 SecurityConfig】，
 * 否则会被注册两次。@Order(HIGHEST_PRECEDENCE) 让它排在 springSecurityFilterChain（order -100）之前，
 * 从而覆盖「鉴权 + 方法级安全 + 控制器 + 序列化」的完整耗时。
 *
 * 注意：现有 JwtAuthenticationFilter 是「@Component + addFilterBefore」双重注册的先例，
 * 之所以没出问题是因为 OncePerRequestFilter 用请求属性去重（同一实例第二次进入直接 no-op）。
 * 本过滤器不依赖这个机制。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTimingFilter extends OncePerRequestFilter {

    /**
     * 不采集的路径前缀（getServletPath() 已剥掉 /check 上下文路径）
     */
    private static final String[] SKIP_PREFIXES = {
            "/actuator",              // 监控端点自身，含 /actuator/prometheus
            "/ws",                    // SockJS/STOMP 握手
            "/api/admin/monitoring",  // 趋势接口自身，仪表盘会轮询，采集会自我放大
            "/static",
            "/assets",
            "/error",
    };

    /**
     * 不采集的静态资源扩展名
     */
    private static final String[] SKIP_SUFFIXES = {
            ".js", ".css", ".map", ".png", ".jpg", ".jpeg",
            ".gif", ".svg", ".ico", ".woff", ".woff2", ".ttf",
    };

    private final ResponseTimeStore store;

    public RequestTimingFilter(ResponseTimeStore store) {
        this.store = store;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (shouldSkip(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            try {
                store.record(elapsedMs, response.getStatus());
            } catch (Exception e) {
                // 采集失败绝不能影响请求本身
                log.debug("记录请求耗时失败: {}", e.getMessage());
            }
        }
    }

    private boolean shouldSkip(String path) {
        if (path == null) {
            return true;
        }
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        String lower = path.toLowerCase();
        for (String suffix : SKIP_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
