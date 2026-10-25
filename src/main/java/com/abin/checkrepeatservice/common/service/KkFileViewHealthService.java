package com.abin.checkrepeatservice.common.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * kkFileView 可用性探测（性能修复 H8）。
 *
 * <p>背景：原实现每次预览都在 Tomcat 请求线程里用 {@code RestTemplate} 同步 ping
 * {@code /index}，连接 5s + 读 30s，kkFileView 一卡就把预览线程按 30 秒挂住。</p>
 *
 * <p>方案：改用 JDK 内置 {@link HttpClient}（自带连接池、支持超时），
 * 由定时线程每隔 {@code interval-ms} 探测一次，结果写入 volatile 缓存；
 * 请求线程只读缓存，绝不发起网络 IO。</p>
 */
@Service
@Slf4j
public class KkFileViewHealthService {

    @Value("${kkfileview.base-url}")
    private String baseUrl;

    /** 探活路径（原硬编码 /index，现可配置） */
    @Value("${kkfileview.health-path:/index}")
    private String healthPath;

    @Value("${kkfileview.health.enabled:true}")
    private boolean enabled;

    @Value("${kkfileview.health.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${kkfileview.health.read-timeout-ms:3000}")
    private int readTimeoutMs;

    /**
     * 初始乐观：首次探测完成前不阻断预览（真挂了浏览器侧也会报错），
     * 避免应用刚启动时把正常请求误判为不可用。
     */
    private volatile boolean available = true;

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 请求线程只读缓存，不做任何网络调用。
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 后台刷新探活结果。绑定在 Spring 调度线程，不占用 Tomcat 线程。
     */
    @Scheduled(initialDelayString = "${kkfileview.health.initial-delay-ms:10000}",
            fixedDelayString = "${kkfileview.health.interval-ms:60000}")
    public void refresh() {
        if (!enabled) {
            return;
        }
        String url = baseUrl + healthPath;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.discarding());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 400;
            if (ok != available) {
                log.info("kkFileView 健康状态变更: {} -> {} (status={}, url={})",
                        available, ok, response.statusCode(), url);
            }
            available = ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("kkFileView 健康探测被中断");
        } catch (Exception e) {
            if (available) {
                log.warn("kkFileView 健康探测失败，预览将降级 - url: {}, 原因: {}", url, e.getMessage());
            }
            available = false;
        }
    }
}
