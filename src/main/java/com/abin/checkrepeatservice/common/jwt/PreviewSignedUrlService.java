package com.abin.checkrepeatservice.common.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 文件预览签名 URL 服务（安全修复 S2 配套）。
 *
 * <p>背景：{@code /api/file/**} 收紧为必须登录后，Office 预览依赖 kkFileView 服务端
 * 回调下载接口，该回调无法携带用户 JWT；直接放行下载接口又会退回 IDOR。
 * 方案：用户通过鉴权的 smartPreview 入口后，由后端签发<b>绑定单个资源（论文文件 / 查重报告）、10 分钟有效</b>
 * 的 HMAC 令牌，kkFileView 用该令牌访问专用的 preview-fetch / preview-fetch-report 接口。
 * 令牌不可枚举、不可跨资源使用、过期即失效。</p>
 *
 * <p>令牌格式：{@code <到期毫秒时间戳>.<HMAC-SHA256hex(secret, resourceKey + "." + exp)>}，
 * resourceKey 对文件取 fileId 字符串，对报告取 {@code report:<reportId>}</p>
 */
@Component
@Slf4j
public class PreviewSignedUrlService {

    /** 预览令牌有效期：10 分钟（覆盖 kkFileView 拉取+转换耗时即可，尽量短） */
    private static final long TTL_MILLIS = 10 * 60 * 1000L;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // 复用 JWT 密钥做 HMAC（prod fail-fast 已保证其强度 ≥32 且非默认值）
    @Value("${spring.jwt.secret-key}")
    private String secretKey;

    /**
     * 为指定文件签发预览令牌
     */
    public String sign(Long fileId) {
        if (fileId == null) {
            throw new IllegalArgumentException("fileId 不能为空");
        }
        return sign(String.valueOf(fileId));
    }

    /**
     * 为任意可预览资源签发令牌。
     *
     * <p>resourceKey 必须带资源类型命名空间（如 {@code report:123}），
     * 否则报告令牌与文件令牌会因 ID 数值相同而互相通用。</p>
     */
    public String sign(String resourceKey) {
        if (resourceKey == null || resourceKey.isEmpty()) {
            throw new IllegalArgumentException("资源 key 不能为空");
        }
        long exp = System.currentTimeMillis() + TTL_MILLIS;
        return exp + "." + hmac(resourceKey + "." + exp);
    }

    /**
     * 校验令牌：文件ID 绑定、未过期、签名一致（常量时间比较防时序侧信道）
     */
    public boolean verify(Long fileId, String token) {
        return verify(fileId == null ? null : String.valueOf(fileId), token);
    }

    /**
     * 校验令牌：资源 key 绑定、未过期、签名一致（常量时间比较防时序侧信道）
     */
    public boolean verify(String resourceKey, String token) {
        if (resourceKey == null || token == null) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return false;
        }
        try {
            long exp = Long.parseLong(token.substring(0, dot));
            if (System.currentTimeMillis() > exp) {
                log.warn("预览令牌已过期 - 资源: {}", resourceKey);
                return false;
            }
            String expected = hmac(resourceKey + "." + exp);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    token.substring(dot + 1).getBytes(StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // 算法固定存在、密钥在 prod 启动时已校验，此处理论不可达
            throw new IllegalStateException("预览令牌签名计算失败", e);
        }
    }
}
