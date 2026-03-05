package com.java.edtech.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@ConfigurationProperties(prefix = "security.jwt")
@Data
public class JwtProperties {
    private String issuer;
    private String secret;
    private int accessTokenMinutes = 15;
    private int refreshTokenDays = 30;
}
