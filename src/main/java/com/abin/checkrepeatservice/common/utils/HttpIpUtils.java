package com.abin.checkrepeatservice.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * IP地址工具类：获取请求真实IP
 */
public class HttpIpUtils {
    // 常见代理请求头（优先级从高到低）
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
    };

    /**
     * 从HttpServletRequest中获取真实IP
     */
    public static String getRealIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        // 遍历代理请求头，获取第一个非空IP
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (!StringUtils.isEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // 多个IP时取第一个（如 X-Forwarded-For: 192.168.1.1, 10.0.0.1）
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        // 无代理时直接获取请求IP
        String remoteAddr = request.getRemoteAddr();
        // 本地环境IP替换为"127.0.0.1"（避免显示"0:0:0:0:0:0:0:1"）
        return "0:0:0:0:0:0:0:1".equals(remoteAddr) ? "127.0.0.1" : remoteAddr;
    }
}
