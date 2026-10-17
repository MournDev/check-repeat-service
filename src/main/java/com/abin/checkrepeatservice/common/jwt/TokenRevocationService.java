package com.abin.checkrepeatservice.common.jwt;

import com.abin.checkrepeatservice.common.utils.JwtUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * JWT 令牌吊销服务（安全修复 S7）。
 *
 * <p>背景：JWT 是无状态令牌，签发后在过期前（默认 24h）始终有效。此前登出只清
 * SecurityContext、改密/管理员重置密码也不影响存量 token，导致：
 * ① 公共电脑登出后 token 被复制仍可用；② 账号被盗后改密，攻击者手里的旧 token
 * 在剩余有效期内依旧畅通；③ 管理员重置某用户密码后该用户旧会话不失效。</p>
 *
 * <p>两层吊销机制（均存 Redis，TTL 与 token 生命周期对齐，不留永久垃圾键）：</p>
 * <ul>
 *   <li><b>单令牌黑名单</b>：登出时把当前 token 的 SHA-256 写入
 *       {@code jwt:revoked:tok:{hash}}，TTL = 该 token 剩余有效期；</li>
 *   <li><b>用户级吊销时间点</b>：改密/重置密码时写
 *       {@code jwt:revoked:before:{userId}} = 当前毫秒时间戳，
 *       凡签发时间（iat）早于该值的 token 一律拒绝，一条记录吊销该用户全部旧会话，
 *       无需逐 token 拉黑；记录 TTL = JWT 最大有效期，过期后所有旧 token 本就失效。</li>
 * </ul>
 *
 * <p><b>降级策略 fail-open</b>：Redis 查询异常时放行（error 日志）。
 * 与登录防爆破一致——缓存故障不应放大为全站强制掉线；此时吊销能力暂时失效，
 * token 自身的过期时间仍是最后防线。</p>
 */
@Slf4j
@Service
public class TokenRevocationService {

    /** 单令牌黑名单：jwt:revoked:tok:{sha256(token)} */
    private static final String TOKEN_KEY_PREFIX = "jwt:revoked:tok:";
    /** 用户级吊销时间点：jwt:revoked:before:{userId} = epochMillis */
    private static final String USER_KEY_PREFIX = "jwt:revoked:before:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtils jwtUtils;

    /** JWT 最大有效期（毫秒），用户级吊销记录按此时长留存，默认 24h（与 spring.jwt.expiration 对齐） */
    @Value("${spring.jwt.expiration:86400000}")
    private long jwtTtlMillis;

    /**
     * 吊销单个令牌（登出调用）。幂等：重复登出、已过期 token 都不报错。
     *
     * @param rawToken 可带 Bearer 前缀，内部统一归一化
     */
    public void revokeToken(String rawToken) {
        String token = jwtUtils.normalizeToken(rawToken);
        if (token == null || token.isBlank() || !jwtUtils.validateTokenFormat(token)) {
            return;
        }
        try {
            long remainingMillis = jwtUtils.getRemainingTtlMillis(token);
            if (remainingMillis <= 0) {
                // 已过期，无需再拉黑
                return;
            }
            stringRedisTemplate.opsForValue().set(
                    TOKEN_KEY_PREFIX + sha256(token), "1",
                    remainingMillis, TimeUnit.MILLISECONDS);
            log.info("令牌已加入登出黑名单，剩余有效期(ms): {}", remainingMillis);
        } catch (Exception e) {
            // 登出时 Redis 写失败不应阻断用户登出流程；token 到期后自然失效
            log.error("令牌加入黑名单失败（token 仍会按期过期）", e);
        }
    }

    /**
     * 吊销指定用户的全部已签发令牌（改密、忘记密码重置、管理员重置密码后调用）。
     * 之后新签发的 token（iat ≥ 吊销时间点）不受影响。
     */
    public void revokeAllTokensForUser(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            long ttlSeconds = Math.max(1, jwtTtlMillis / 1000);
            stringRedisTemplate.opsForValue().set(
                    USER_KEY_PREFIX + userId,
                    String.valueOf(System.currentTimeMillis()),
                    ttlSeconds, TimeUnit.SECONDS);
            log.info("用户全部旧令牌已吊销 - userId: {}", userId);
        } catch (Exception e) {
            // 与单令牌吊销不同：这里的失败会让旧会话继续可用，需要明确告警
            log.error("用户级令牌吊销写入失败，该用户旧 token 在过期前仍可用 - userId: {}", userId, e);
        }
    }

    /**
     * 判断令牌是否已被吊销（过滤器在签名/过期校验通过后调用）。
     *
     * @return true 表示命中黑名单或签发早于用户吊销时间点，应拒绝建立认证
     */
    public boolean isRevoked(String rawToken) {
        String token = jwtUtils.normalizeToken(rawToken);
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            // 1. 单令牌黑名单（登出）
            Boolean blacklisted = stringRedisTemplate.hasKey(TOKEN_KEY_PREFIX + sha256(token));
            if (Boolean.TRUE.equals(blacklisted)) {
                return true;
            }

            // 2. 用户级吊销时间点（改密/重置）
            Long userId = jwtUtils.extractUserId(token);
            if (userId != null) {
                String revokeBefore = stringRedisTemplate.opsForValue().get(USER_KEY_PREFIX + userId);
                if (revokeBefore != null && !revokeBefore.isBlank()) {
                    long issuedAt = jwtUtils.extractIssuedAtMillis(token);
                    // iat 严格早于吊销时间点即旧令牌（同一毫秒内签发的极端情况视为新令牌放行）
                    if (issuedAt > 0 && issuedAt < Long.parseLong(revokeBefore.trim())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (NumberFormatException e) {
            // 吊销标记值异常不应误伤正常请求
            log.warn("用户级吊销标记值非法，按未吊销处理");
            return false;
        } catch (Exception e) {
            // fail-open：Redis 故障不强制下线
            log.error("令牌吊销状态查询异常，本次放行（fail-open）", e);
            return false;
        }
    }

    /** token 原文不入库，只存 SHA-256 摘要，避免 Redis 数据泄露直接暴露可用凭证 */
    private String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // SHA-256 是 JDK 内置算法，理论不可达
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
