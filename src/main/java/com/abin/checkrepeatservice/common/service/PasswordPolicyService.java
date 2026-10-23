package com.abin.checkrepeatservice.common.service;

import com.abin.checkrepeatservice.admin.mapper.SystemConfigMapper;
import com.abin.checkrepeatservice.common.event.SecurityConfigChangedEvent;
import com.abin.checkrepeatservice.pojo.entity.SystemConfig;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 密码强度策略。
 *
 * <p>策略来源为后台安全配置 {@code security_config}：
 * {@code passwordMinLength} 与 {@code passwordRequirements}
 * （uppercase / lowercase / numbers / special）。保存后最多 10 秒生效。</p>
 *
 * <p>未保存过安全配置时保持系统原有行为（最少 6 位、无字符组合要求），
 * 避免升级后突然拦截既有用户的改密流程。</p>
 */
@Slf4j
@Service
public class PasswordPolicyService {

    private static final String SECURITY_CONFIG_KEY = "security_config";
    private static final long CACHE_MILLIS = 10_000L;
    private static final int DEFAULT_MIN_LENGTH = 6;

    private volatile PasswordPolicy policyCache;
    private volatile long policyCachedAt;

    @Resource
    private SystemConfigMapper systemConfigMapper;

    /**
     * 安全配置保存/刷新后立即失效，下一次校验即读取新策略。
     */
    @EventListener
    public void onSecurityConfigChanged(SecurityConfigChangedEvent event) {
        policyCache = null;
        policyCachedAt = 0L;
        log.info("安全配置已变更（{}），密码策略缓存已失效", event.configKey());
    }

    /**
     * 校验密码是否符合当前生效策略。
     *
     * @return null 表示通过；否则返回可直接展示给用户的中文提示
     */
    public String validate(String password) {
        if (password == null || password.isEmpty()) {
            return "密码不能为空";
        }
        PasswordPolicy policy = currentPolicy();
        if (password.length() < policy.minLength()) {
            return "密码长度不能少于" + policy.minLength() + "位";
        }
        if (policy.requireUppercase() && password.chars().noneMatch(Character::isUpperCase)) {
            return "密码必须包含大写字母";
        }
        if (policy.requireLowercase() && password.chars().noneMatch(Character::isLowerCase)) {
            return "密码必须包含小写字母";
        }
        if (policy.requireNumber() && password.chars().noneMatch(Character::isDigit)) {
            return "密码必须包含数字";
        }
        if (policy.requireSpecial() && password.chars().allMatch(Character::isLetterOrDigit)) {
            return "密码必须包含特殊字符";
        }
        return null;
    }

    private PasswordPolicy currentPolicy() {
        long now = System.currentTimeMillis();
        PasswordPolicy cached = policyCache;
        if (cached != null && now - policyCachedAt < CACHE_MILLIS) {
            return cached;
        }
        PasswordPolicy loaded = loadPolicy();
        policyCache = loaded;
        policyCachedAt = now;
        return loaded;
    }

    private PasswordPolicy loadPolicy() {
        PasswordPolicy fallback = new PasswordPolicy(DEFAULT_MIN_LENGTH, false, false, false, false);
        try {
            SystemConfig config = systemConfigMapper.selectByConfigKey(SECURITY_CONFIG_KEY);
            if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
                return fallback;
            }
            Map<String, Object> map = JSON.parseObject(config.getConfigValue(), Map.class);
            int minLength = asPositiveInt(map.get("passwordMinLength"), DEFAULT_MIN_LENGTH);
            List<?> requirements = map.get("passwordRequirements") instanceof List<?> list
                    ? list : List.of();
            boolean upper = false;
            boolean lower = false;
            boolean number = false;
            boolean special = false;
            for (Object item : requirements) {
                if (item == null) {
                    continue;
                }
                switch (item.toString().trim().toLowerCase()) {
                    case "uppercase" -> upper = true;
                    case "lowercase" -> lower = true;
                    case "numbers", "number", "digits" -> number = true;
                    case "special", "symbols", "specialcharacters" -> special = true;
                    default -> { }
                }
            }
            return new PasswordPolicy(minLength, upper, lower, number, special);
        } catch (Exception e) {
            log.warn("读取密码策略失败，沿用默认策略（最少 {} 位）", DEFAULT_MIN_LENGTH, e);
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

    private record PasswordPolicy(int minLength, boolean requireUppercase, boolean requireLowercase,
                                  boolean requireNumber, boolean requireSpecial) {
    }
}
