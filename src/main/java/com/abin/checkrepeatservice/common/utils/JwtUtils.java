package com.abin.checkrepeatservice.common.utils;

import com.abin.checkrepeatservice.common.jwt.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

    private final JwtProperties jwtProperties;

    // 构造函数注入配置属性
    public JwtUtils(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 获取签名密钥
     */
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes());
    }

    /**
     * 归一化令牌：去掉可能存在的令牌前缀（默认 Bearer，取自 spring.jwt.token-prefix）与首尾空白。
     *
     * 背景：调用方有两种写法 —— 一部分直接把 Authorization 请求头整体传进来
     * （形如 "Bearer eyJhbGci..."），另一部分已经自己剥过前缀。本类是所有调用方的
     * 唯一入口，在这里统一归一化，两种写法就都能正常工作，也避免每个 Controller
     * 各自手写 substring(7) / replace("Bearer ", "") 再出偏差。
     *
     * 注：前缀后必须是空白或字符串结束才认为是真正的前缀，
     * 这样 "BearerXxx" 这类令牌不会被误截断。
     */
    public String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String value = token.trim();
        if (value.isEmpty()) {
            return value;
        }

        String prefix = jwtProperties.getTokenPrefix();
        // 配置未显式给出时与 JwtAuthenticationFilter 的默认值保持一致
        if (!StringUtils.hasText(prefix)) {
            prefix = "Bearer";
        }
        prefix = prefix.trim();

        int prefixLength = prefix.length();
        if (prefixLength > 0
                && value.length() > prefixLength
                && value.regionMatches(true, 0, prefix, 0, prefixLength)
                && Character.isWhitespace(value.charAt(prefixLength))) {
            value = value.substring(prefixLength).trim();
        }
        return value;
    }

    /**
     * 生成JWT token
     */
    public String generateToken(Long userId, String username, String roleCode) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roleCode", roleCode)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 2. 从Token中解析用户ID（核心方法，适配拦截器需求）
     * @param token JWT Token字符串
     * @return 解析出的用户ID（Long类型）
     * @throws Exception Token无效、过期、签名错误时抛异常
     */
    public Long getUserIdFromToken(String token) throws Exception {
        try {
            // 1. 生成加密密钥（与生成Token时的密钥一致）
            SecretKey key = getSecretKey();

            // 2. 解析Token（校验签名、过期时间）
            //    先归一化：调用方可能传的是整个 Authorization 头（含 "Bearer " 前缀）
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(normalizeToken(token))
                    .getPayload();
            // 3. 从payload中获取用户ID（主题字段=用户ID，转为Long类型）
            return claims.get("userId", Long.class);

        } catch (ExpiredJwtException e) {
            // Token已过期
            throw new Exception("Token已过期，请重新登录", e);
        } catch (UnsupportedJwtException e) {
            // Token格式不支持（如不是JWT格式）
            throw new Exception("Token格式不支持", e);
        } catch (MalformedJwtException e) {
            // Token格式错误（如签名部分缺失、格式混乱）
            throw new Exception("Token格式错误，请检查Token有效性", e);
        } catch (SignatureException e) {
            // Token签名错误（如密钥不匹配、Token被篡改）
            throw new Exception("Token签名错误，可能已被篡改", e);
        } catch (IllegalArgumentException e) {
            // Token参数为空或解析失败
            throw new Exception("Token无效，无法解析用户信息", e);
        }
    }

    /**
     * 从token中提取所有声明
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(normalizeToken(token))
                .getPayload();
    }

    /**
     * 从token中提取用户名
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * 检查token是否过期
     */
    public Boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    /**
     * 验证token有效性
     */
    public Boolean validateToken(String token, String username) {
        // 验证token格式和Bearer前缀
        if (!validateTokenFormat(token)) {
            return false;
        }
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }


    /**
     * 获取token过期时间
     */
    public Date getExpirationDate(String token) {
        return extractAllClaims(token).getExpiration();
    }

    /**
     * 获取token签发时间（epoch 毫秒）。S7 令牌吊销：与用户级「吊销时间点」比较，
     * 判定该 token 是否签发于改密/重置之前。
     */
    public long extractIssuedAtMillis(String token) {
        Date issuedAt = extractAllClaims(token).getIssuedAt();
        return issuedAt == null ? 0L : issuedAt.getTime();
    }

    /**
     * token 剩余有效期（毫秒）；已过期返回 0。用于吊销键 TTL 设置，
     * 使黑名单/吊销标记随 token 自然过期而自动清理，不产生永久垃圾键。
     */
    public long getRemainingTtlMillis(String token) {
        long remaining = getExpirationDate(token).getTime() - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    /**
     *
     * @param token
     * 获取用户ID
     * @return
     */
    public Long extractUserId(String token) {
        return extractAllClaims(token).get("userId", Long.class);
    }

    /**
     *
     * @param token
     * 获取角色编码
     * @return
     */
    public String extractRoleCode(String token) {
        return extractAllClaims(token).get("roleCode", String.class);
    }
    /**
     * 验证token格式
     */
    public boolean validateTokenFormat(String token) {
        String value = normalizeToken(token);
        if (value == null || value.isEmpty()) {
            return false;
        }
        String[] parts = value.split("\\.");
        return parts.length == 3; // JWT 应由三部分组成
    }


}
