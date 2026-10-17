package com.abin.checkrepeatservice.common.xss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * XSS 全局输入过滤配置（安全修复 S9）。
 *
 * <pre>
 * xss:
 *   enabled: true                  # 总开关
 *   max-body-size: 10485760        # JSON 请求体最大缓存字节（10MB），超限不过滤直接放行
 *   excludes:                      # 路径前缀白名单（相对 context-path /check，如 /ws/）
 *     - /ws/
 *   rich-text-fields:              # 富文本字段：保留安全标签，移除 script 等危险内容
 *     - reviewOpinion
 *   skip-fields:                   # 原样放行字段（凭据/令牌绝不能被 HTML 转义篡改）
 *     - password
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "xss")
public class XssProperties {

    /** 全局开关，默认开启 */
    private boolean enabled = true;

    /**
     * JSON 请求体缓存上限（字节）。过滤器需把 body 整体读入内存清洗后再放行，
     * 超过该大小的请求不做 body 过滤（原样转发），避免大包打爆堆内存；
     * 默认 10MB，业务侧最大 JSON 为管理端配置导入。
     */
    private int maxBodySize = 10 * 1024 * 1024;

    /** 排除路径前缀（servletPath，不含 context-path），命中即跳过整个过滤器 */
    private List<String> excludes = new ArrayList<>(List.of(
            "/ws/",          // SockJS/STOMP 握手与帧，非 JSON 业务表单
            "/actuator/",    // 运维端点
            "/error"         // 容器错误转发
    ));

    /**
     * 富文本字段名（大小写不敏感）：走 Jsoup relaxed 白名单清洗，
     * 保留 p/br/strong/em/u/span/ul/ol/li/a 等安全标签与 http(s) 链接。
     * - reviewOpinion：教师评阅意见（multipart 入口，Service 层已有同款清洗，列入防 JSON 变体）
     * - contentTemplate：管理员维护的消息/邮件模板（经 MimeMessage HTML 发送）
     */
    private List<String> richTextFieldNames = new ArrayList<>(List.of(
            "reviewOpinion",
            "contentTemplate"
    ));

    /**
     * 原样放行字段名（大小写不敏感）。这些字段一旦被 HTML 转义（如 &lt;、&amp;）
     * 会直接改变凭据/令牌的真实值，造成无法登录、签名失败等事故。
     */
    private List<String> skipFieldNames = new ArrayList<>(List.of(
            "password",
            "oldPassword",
            "newPassword",
            "confirmPassword",
            "passwordConfirm",
            "token",
            "oldToken",
            "accessToken",
            "refreshToken",
            "secret",
            "secretKey",
            "authorization",
            "authCode"
    ));
}
