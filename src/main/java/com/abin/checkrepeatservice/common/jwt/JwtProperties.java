package com.abin.checkrepeatservice.common.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Component
@ConfigurationProperties(prefix = "spring.jwt")
@Data
public class JwtProperties {
    private String secretKey;
    private Long expiration;
    private String tokenHeader;
    private String tokenPrefix;
}

