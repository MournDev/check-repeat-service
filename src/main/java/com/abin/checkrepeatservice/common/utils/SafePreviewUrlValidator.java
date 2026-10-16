package com.abin.checkrepeatservice.common.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 在线预览 URL 安全校验器（安全修复 P0：SSRF 防护）。
 *
 * <p>背景：/api/file/onlinePreview?url=xxx 原来匿名可访问，且原样把任意外部/内网 URL
 * 交给内网 kkFileView 拉取并 302 跳转，构成 SSRF + 开放重定向（可探测
 * http://169.254.169.254/、内网 MySQL/Redis、管理端口等）。</p>
 *
 * <p>校验规则：</p>
 * <ol>
 *   <li>仅允许 http/https 绝对地址，禁止 userInfo（user:pass@host）；</li>
 *   <li>host 命中显式白名单（file.preview.allowed-hosts 配置，自动并入 MinIO
 *       public-endpoint 的 host）时直接放行，白名单支持子域后缀匹配——容器网络别名
 *       这类无法做公网 DNS 解析的主机只能靠显式白名单；</li>
 *   <li>未命中白名单时做 DNS 解析，解析结果落在回环/任意本地/链路本地/站点内网/
 *       组播地址段的一律拒绝。</li>
 * </ol>
 */
@Component
@Slf4j
public class SafePreviewUrlValidator {

    /**
     * 额外允许的 host（精确或子域后缀），逗号分隔。
     * 例如：check-repeat-system.xyz,.example-cdn.com
     */
    @Value("${file.preview.allowed-hosts:}")
    private String allowedHostsConfig;

    /** MinIO 浏览器可达地址的 host 自动并入白名单 */
    @Value("${minio.public-endpoint:${minio.endpoint:}}")
    private String minioPublicEndpoint;

    private List<String> allowedHosts;

    @PostConstruct
    public void init() {
        List<String> hosts = new java.util.ArrayList<>(Arrays.stream(allowedHostsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList());
        String minioHost = extractHost(minioPublicEndpoint);
        if (minioHost != null && !hosts.contains(minioHost)) {
            hosts.add(minioHost);
        }
        allowedHosts = hosts;
        log.info("在线预览 URL 白名单初始化完成: {}", allowedHosts);
    }

    /**
     * 校验待预览 URL，不合法抛出 IllegalArgumentException（消息可直接返回给调用方）
     */
    public void validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("预览地址不能为空");
        }

        final URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("预览地址格式非法");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("仅允许 http/https 协议的预览地址");
        }

        // 禁止携带用户信息，防止借助 userInfo 混淆 host 解析
        if (uri.getRawUserInfo() != null || rawUrl.contains("@")) {
            throw new IllegalArgumentException("预览地址不允许包含用户信息");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("预览地址缺少主机名");
        }
        host = host.toLowerCase(Locale.ROOT);

        // 1. 显式白名单：精确匹配或子域后缀匹配
        if (isWhitelisted(host)) {
            return;
        }

        // 2. 非白名单 host：必须解析为公网地址，阻断内网/回环/链路本地探测
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isInternalAddress(address)) {
                    log.warn("SSRF 拦截：预览地址 host={} 解析到内网/保留地址 {}", host, address.getHostAddress());
                    throw new IllegalArgumentException("预览地址指向不允许的内网地址");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("预览地址 DNS 解析失败: host={}", host, e);
            throw new IllegalArgumentException("预览地址无法解析");
        }
    }

    private boolean isWhitelisted(String host) {
        for (String allowed : allowedHosts) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInternalAddress(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        // 手动覆盖 IPv6 唯一本地地址 fc00::/7（InetAddress 无对应判断方法）
        byte[] bytes = address.getAddress();
        if (bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC) {
            return true;
        }
        return false;
    }

    private String extractHost(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(endpoint.trim()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }
}
