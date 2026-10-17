package com.abin.checkrepeatservice.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * IP地址工具类：获取请求真实IP
 *
 * S15 信任链口径（唯一裁决点，登录日志/操作日志/报告验真限流均走这里）：
 * 1. 仅当直连对端（remoteAddr）是内网/回环地址——即请求确实经过本方反向代理——才信任
 *    X-Forwarded-For 等代理头；公网直连时一律取 remoteAddr，防止客户端伪造代理头漂白来源；
 * 2. 代理链自右向左取第一个格式合法的 IP：最右一跳由可信代理追加，最难伪造
 *    （客户端自带的 "X-Forwarded-For: 1.2.3.4" 会被 nginx 追加真实 IP 到末尾）；
 * 3. 所有取出的 IP 必须通过 IPv4/IPv6 格式校验，杜绝头注入把垃圾串写进日志/限流键。
 */
public class HttpIpUtils {
    // 常见代理请求头（优先级从高到低）
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
    };

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    /** IPv6 宽松校验（十六进制组 + 冒号，支持 :: 压缩），足以拦截注入垃圾 */
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "([0-9a-fA-F]{0,4}:){1,7}[0-9a-fA-F]{0,4}");

    /**
     * 从HttpServletRequest中获取真实IP
     */
    public static String getRealIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = request.getRemoteAddr();
        if (isInternalIp(remoteAddr)) {
            for (String header : IP_HEADERS) {
                String value = request.getHeader(header);
                if (StringUtils.isEmpty(value) || "unknown".equalsIgnoreCase(value)) {
                    continue;
                }
                String ip = pickValidIp(value);
                if (ip != null) {
                    return normalize(ip);
                }
            }
        }
        return normalize(remoteAddr);
    }

    /**
     * 代理链自右向左取第一个格式合法的 IP（最右一跳由可信代理追加）
     */
    private static String pickValidIp(String chain) {
        String[] parts = chain.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (isValidIp(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty() || ip.length() > 45) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches() || IPV6_PATTERN.matcher(ip).matches();
    }

    /**
     * 内网/回环地址判定：命中即认为直连对端是本方反向代理（或内网调用方）
     */
    private static boolean isInternalIp(String ip) {
        if (ip == null) {
            return false;
        }
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("169.254.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            // 172.16.0.0/12
            String[] parts = ip.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    // 非法格式按外网处理
                }
            }
        }
        String lower = ip.toLowerCase();
        // IPv6 唯一本地地址 fc00::/7 与链路本地 fe80::/10
        return lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe80");
    }

    private static String normalize(String ip) {
        // 本地环境IP替换为"127.0.0.1"（避免显示"0:0:0:0:0:0:0:1"）
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }
}
