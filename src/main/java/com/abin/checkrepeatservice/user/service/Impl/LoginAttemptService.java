package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.SystemConfigMapper;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SystemConfig;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录防爆破服务（安全修复 S6）。
 *
 * <p>背景：登录接口此前对失败次数零限制，匿名攻击者可无上限枚举密码
 * （配合 S1 前的注册提权、S2 前的文件 IDOR 属同一批匿名攻击面）。</p>
 *
 * <p>策略（固定窗口 + Redis 计数，与找回密码验证码限流同一套风格）：</p>
 * <ul>
 *   <li>账号维度：同一用户名在窗口内连续失败达 {@code user-max-failures} 次，
 *       窗口剩余时间直接拒绝该账号登录，阻断「换 IP 爆破指定账号」；</li>
 *   <li>IP 维度：同一 IP 失败达 {@code ip-max-failures} 次，窗口内拒绝，
 *       阻断「单 IP 批量扫账号」。阈值更宽，避免误伤校园网/NAT 出口的正常用户；</li>
 *   <li>登录成功只清账号计数、<b>不清</b> IP 计数，防止「每试 N-1 次登录一次自己
 *       账号」无限清零；</li>
 *   <li>计数键首次写入即带窗口 TTL，窗口过后自动解锁，无需人工介入。</li>
 * </ul>
 *
 * <p><b>降级策略：fail-open。</b>Redis 不可用时防爆破检查放行（仅 error 日志），
 * 避免缓存故障放大为全站无法登录；此时安全能力暂时退化为仅靠 BCrypt 与登录日志。</p>
 */
@Slf4j
@Service
public class LoginAttemptService {

    /** 账号维度失败计数键：login_fail:user:{username} */
    private static final String USER_KEY_PREFIX = "login_fail:user:";
    /** IP 维度失败计数键：login_fail:ip:{ip} */
    private static final String IP_KEY_PREFIX = "login_fail:ip:";

    /** HttpIpUtils 取不到真实地址时的占位值，该值不参与 IP 维度限流（无法归因到真实来源） */
    private static final String UNKNOWN_IP = "unknown";

    @Value("${login.security.user-max-failures:5}")
    private int userMaxFailures;

    @Value("${login.security.ip-max-failures:20}")
    private int ipMaxFailures;

    @Value("${login.security.window-seconds:900}")
    private long windowSeconds;

    /** 后台安全配置的缓存有效期：保存后最多 10 秒生效，避免每次登录都查库 */
    private static final long POLICY_CACHE_MILLIS = 10_000L;
    private static final String SECURITY_CONFIG_KEY = "security_config";

    private volatile LoginPolicy policyCache;
    private volatile long policyCachedAt;

    @Resource
    private SystemConfigMapper systemConfigMapper;

    /*
     * 刻意复用 StringRedisTemplate（同 AuthServiceImpl 找回密码限流）：
     * 计数需要 increment 后再 get，JDK 序列化的 RedisTemplate 读不回裸整数。
     */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 登录前置检查：账号或 IP 已处于锁定窗口内则直接拒绝（不执行密码校验，
     * 避免锁定期间继续消耗 BCrypt 算力）。
     */
    public void assertLoginAllowed(String username, String clientIp) {
        LoginPolicy policy = currentPolicy();
        if (!policy.enabled()) {
            return;
        }
        try {
            // 1. 账号维度（优先判定：提示对用户最明确）
            long userFails = readCount(USER_KEY_PREFIX + username);
            if (userFails >= policy.userMaxFailures()) {
                log.warn("账号处于登录锁定窗口 - username: {}, fails: {}, ip: {}",
                        username, userFails, clientIp);
                throw new BusinessException(ResultCode.LOGIN_TOO_MANY_ATTEMPTS,
                        "账号或密码连续错误次数过多，为保护账号安全已临时锁定，请"
                                + lockMinutesText(ttlOf(USER_KEY_PREFIX + username, policy.windowSeconds())) + "后再试",
                        null);
            }

            // 2. IP 维度（取不到真实 IP 时跳过，避免把全部来源不明请求聚到一个键）
            if (isRealIp(clientIp)) {
                long ipFails = readCount(IP_KEY_PREFIX + clientIp);
                if (ipFails >= policy.ipMaxFailures()) {
                    log.warn("IP 处于登录限流窗口 - ip: {}, fails: {}, username: {}",
                            clientIp, ipFails, username);
                    throw new BusinessException(ResultCode.LOGIN_TOO_MANY_ATTEMPTS,
                            "当前网络登录尝试过于频繁，已临时限制，请"
                                    + lockMinutesText(ttlOf(IP_KEY_PREFIX + clientIp, policy.windowSeconds())) + "后再试",
                            null);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // fail-open：Redis 故障不阻断登录
            log.error("登录防爆破检查异常，本次放行（fail-open）- username: {}", username, e);
        }
    }

    /**
     * 记录一次登录失败：两个维度各自累加。
     *
     * @return 本次失败后的计数结果（是否已触发锁定、剩余尝试次数、窗口时长）
     */
    public FailureOutcome recordLoginFailure(String username, String clientIp) {
        LoginPolicy policy = currentPolicy();
        if (!policy.enabled()) {
            return FailureOutcome.notLocked(policy.userMaxFailures(), policy.windowSeconds(), false);
        }
        long userFails;
        long ipFails = 0;
        try {
            userFails = incrementWithExpiry(USER_KEY_PREFIX + username, policy.windowSeconds());
        } catch (Exception e) {
            log.warn("账号维度登录失败计数写入异常 - username: {}", username, e);
            // fail-open：计数写不进去就当未锁定放行，避免影响正常认证流程
            return FailureOutcome.notLocked(policy.userMaxFailures(), policy.windowSeconds(), true);
        }
        boolean ipLocked = false;
        if (isRealIp(clientIp)) {
            try {
                ipFails = incrementWithExpiry(IP_KEY_PREFIX + clientIp, policy.windowSeconds());
                ipLocked = ipFails >= policy.ipMaxFailures();
            } catch (Exception e) {
                log.warn("IP 维度登录失败计数写入异常 - ip: {}", clientIp, e);
            }
        }

        boolean userLocked = userFails >= policy.userMaxFailures();
        if (userLocked) {
            log.warn("账号已触发登录锁定 - username: {}, fails: {}", username, userFails);
        }
        if (ipLocked) {
            log.warn("IP 已触发登录限流 - ip: {}, fails: {}", clientIp, ipFails);
        }
        long remaining = Math.max(0, policy.userMaxFailures() - userFails);
        return new FailureOutcome(userLocked || ipLocked, userLocked, remaining, policy.windowSeconds(), true);
    }

    /**
     * 登录成功后的处理：清除账号失败计数。
     * 注意不清 IP 计数（理由见类注释）。
     */
    public void recordLoginSuccess(String username) {
        try {
            stringRedisTemplate.delete(USER_KEY_PREFIX + username);
        } catch (Exception e) {
            // 清理失败不影响登录，计数自己会随窗口过期
            log.warn("登录成功后清除失败计数异常 - username: {}", username, e);
        }
    }

    private long readCount(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            // 键值异常时按 0 处理，避免脏数据造成永久锁定
            log.warn("登录失败计数键值非法，按 0 处理 - key: {}", key);
            return 0L;
        }
    }

    /**
     * 失败计数 +1；首次计数（值为 1）时设置窗口 TTL，
     * increment 不会继承任何已有过期时间，必须显式补。
     */
    private long incrementWithExpiry(String key, long ttlSeconds) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        long current = count == null ? 0L : count;
        if (current == 1L) {
            stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        return current;
    }

    /**
     * 取键剩余 TTL（秒）。-1（无 TTL）/-2（不存在）等异常结果一律回退为完整窗口时长，
     * 保证给用户的提示不会出现「请 0 分钟后再试」。
     */
    private long ttlOf(String key, long fallbackSeconds) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl <= 0) {
            return fallbackSeconds;
        }
        return ttl;
    }

    private boolean isRealIp(String clientIp) {
        return clientIp != null && !clientIp.isBlank() && !UNKNOWN_IP.equalsIgnoreCase(clientIp);
    }

    /** 剩余锁定时间的中文文案，最少显示 1 分钟 */
    private String lockMinutesText(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return minutes + " 分钟";
    }

    /**
     * 当前生效的登录锁定策略。
     *
     * <p>优先读后台安全配置 security_config（loginLockEnabled / maxFailedAttempts /
     * lockDuration），10 秒缓存；未配置或读取失败时退回 application.yml 的
     * login.security.* 默认值。</p>
     */
    private LoginPolicy currentPolicy() {
        long now = System.currentTimeMillis();
        LoginPolicy cached = policyCache;
        if (cached != null && now - policyCachedAt < POLICY_CACHE_MILLIS) {
            return cached;
        }
        LoginPolicy loaded = loadPolicy();
        policyCache = loaded;
        policyCachedAt = now;
        return loaded;
    }

    private LoginPolicy loadPolicy() {
        LoginPolicy fallback = new LoginPolicy(true, userMaxFailures, ipMaxFailures,
                windowSeconds <= 0 ? 900 : windowSeconds);
        try {
            SystemConfig config = systemConfigMapper.selectByConfigKey(SECURITY_CONFIG_KEY);
            if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
                return fallback;
            }
            Map<String, Object> map = JSON.parseObject(config.getConfigValue(), Map.class);
            boolean enabled = !(map.get("loginLockEnabled") instanceof Boolean b && !b);
            int maxFailed = asPositiveInt(map.get("maxFailedAttempts"), fallback.userMaxFailures());
            int lockMinutes = asPositiveInt(map.get("lockDuration"),
                    (int) Math.max(1, fallback.windowSeconds() / 60));
            return new LoginPolicy(enabled, maxFailed, fallback.ipMaxFailures(), lockMinutes * 60L);
        } catch (Exception e) {
            log.warn("读取安全配置失败，登录锁定沿用 yml 默认值", e);
            return fallback;
        }
    }

    private int asPositiveInt(Object value, int defaultValue) {
        if (value instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                int parsed = Integer.parseInt(s.trim());
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // 非法值走默认值
            }
        }
        return defaultValue;
    }

    /**
     * 生效中的登录锁定策略（enabled=false 时完全关闭账号/IP 锁定）。
     */
    private record LoginPolicy(boolean enabled, int userMaxFailures, int ipMaxFailures,
                               long windowSeconds) {
    }

    /**
     * 登录失败计数结果。
     */
    @Getter
    public static class FailureOutcome {
        /** 本次失败后是否已触发任一维度的锁定/限流 */
        private final boolean locked;
        /** 触发的是否为账号维度（false 且 locked=true 时为 IP 维度） */
        private final boolean userLocked;
        /** 账号维度剩余可尝试次数（不为负数） */
        private final long remainingAttempts;
        /** 锁定窗口时长（秒），用于拼装提示文案 */
        private final long windowSeconds;
        /** 当前是否启用登录锁定（后台可关闭，关闭时不再提示"再错 N 次锁定"） */
        private final boolean lockingEnabled;

        public FailureOutcome(boolean locked, boolean userLocked,
                              long remainingAttempts, long windowSeconds, boolean lockingEnabled) {
            this.locked = locked;
            this.userLocked = userLocked;
            this.remainingAttempts = remainingAttempts;
            this.windowSeconds = windowSeconds;
            this.lockingEnabled = lockingEnabled;
        }

        static FailureOutcome notLocked(int userMaxFailures, long windowSeconds, boolean lockingEnabled) {
            return new FailureOutcome(false, false, userMaxFailures, windowSeconds, lockingEnabled);
        }
    }
}
