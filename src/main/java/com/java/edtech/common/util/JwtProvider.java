package com.java.edtech.common.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.springframework.stereotype.Component;

import com.java.edtech.common.config.JwtProperties;
import com.java.edtech.domain.entity.AppUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtProvider {
    private final JwtProperties properties;

    public String generateAccessToken(AppUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(properties.getAccessTokenMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(getKey())
                .compact();
    }

    public String generateRefreshToken(AppUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(properties.getRefreshTokenDays(), ChronoUnit.DAYS);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getId().toString())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(getKey())
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return properties.getAccessTokenMinutes() * 60L;
    }

    public long getRefreshTokenTtlSeconds() {
        return properties.getRefreshTokenDays() * 86400L;
    }

    private byte[] getSecretBytes() {
        String secret = properties.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private javax.crypto.SecretKey getKey() {
        return Keys.hmacShaKeyFor(getSecretBytes());
    }
}
