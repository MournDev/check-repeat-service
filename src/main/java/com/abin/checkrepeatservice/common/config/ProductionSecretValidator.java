package com.abin.checkrepeatservice.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 生产环境密钥 fail-fast 校验器（安全修复 P0）。
 *
 * <p>背景：此前 MySQL/Redis 真实密码、QQ 邮箱授权码、JWT 弱密钥、报告验真盐均以默认值
 * 形式硬编码在 application*.yml 并进入 Git 历史，环境变量漏配时系统带着公开密钥上线，
 * 任何人可用公开密钥伪造任意用户 JWT、伪造报告验真哈希。</p>
 *
 * <p>策略：仅在 prod profile 下生效。启动时逐项校验关键凭据，缺失/为空/命中已知开发
 * 默认值（含历史泄露值）即抛出异常拒绝启动，避免「静默带病上线」。本地默认 profile
 * 不受影响，仍可用开发默认值启动。</p>
 */
@Component
@Slf4j
public class ProductionSecretValidator {

    /** 已知的开发默认值 / 历史泄露值，生产环境一律拒绝 */
    private static final Set<String> FORBIDDEN_SECRETS = Set.of(
            "minioadmin",
            "123456",
            "89864243",
            "your_password",
            "change-this-secret-key-in-production",
            "paperCheckSystem2024SecretKey123456",
            "check-repeat-report-salt",
            "dev-only-jwt-secret-key-do-not-use-in-prod-0123456789",
            "dev-only-report-hash-salt-do-not-use-in-prod-0123456789"
    );

    @Resource
    private Environment environment;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.jwt.secret-key:}")
    private String jwtSecretKey;

    @Value("${minio.secretkey:}")
    private String minioSecretKey;

    @Value("${REPORT_HASH_SALT:}")
    private String reportHashSalt;

    @PostConstruct
    public void validate() {
        // 仅生产 profile 强制；本地/测试 profile 允许使用开发默认值
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        List<String> violations = new ArrayList<>();

        checkRequired(violations, "MYSQL_PASSWORD (spring.datasource.password)", datasourcePassword, 8);
        checkRequired(violations, "REDIS_PASSWORD (spring.data.redis.password)", redisPassword, 8);
        checkRequired(violations, "JWT_SECRET_KEY (spring.jwt.secret-key)", jwtSecretKey, 32);
        checkRequired(violations, "MINIO_SECRETKEY (minio.secretkey)", minioSecretKey, 8);
        checkRequired(violations, "REPORT_HASH_SALT", reportHashSalt, 16);

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n================ 生产环境密钥校验未通过，拒绝启动 ================\n");
            sb.append("请通过环境变量注入合规密钥后重新启动（见 .env.example）：\n");
            for (String v : violations) {
                sb.append("  - ").append(v).append('\n');
            }
            sb.append("提示：当前 Git 历史中的旧密钥必须视为已泄露，请同步轮换。\n");
            sb.append("================================================================");
            log.error(sb.toString());
            throw new IllegalStateException(sb.toString());
        }

        log.info("生产环境密钥校验通过（5 项关键凭据均已合规注入）");
    }

    private void checkRequired(List<String> violations, String name, String value, int minLength) {
        if (value == null || value.isBlank()) {
            violations.add(name + " 未配置");
            return;
        }
        if (FORBIDDEN_SECRETS.contains(value.trim())) {
            violations.add(name + " 仍在使用开发默认值/已知泄露值，必须更换");
            return;
        }
        if (value.trim().length() < minLength) {
            violations.add(name + " 长度不足，要求至少 " + minLength + " 个字符");
        }
    }
}
