package com.java.edtech.service.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.java.edtech.api.auth.dto.LoginRequest;
import com.java.edtech.api.auth.dto.RegisterRequest;
import com.java.edtech.api.auth.dto.RefreshRequest;
import com.java.edtech.api.auth.dto.TokenResponse;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.common.util.JwtProvider;
import com.java.edtech.domain.entity.AppUser;
import com.java.edtech.domain.entity.RefreshToken;
import com.java.edtech.repository.AppUserRepository;
import com.java.edtech.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public TokenResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials"));
        if (!user.isActive()) {
            throw new AppException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "User is inactive");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
        }

        String accessToken = jwtProvider.generateAccessToken(user);
        String refreshTokenValue = jwtProvider.generateRefreshToken(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setExpiryDate(Instant.now().plusSeconds(jwtProvider.getRefreshTokenTtlSeconds()));
        refreshTokenRepository.save(refreshToken);

        return buildTokenResponse("Login successful", accessToken, refreshTokenValue, user);
    }

    public TokenResponse register(RegisterRequest request) {
        appUserRepository.findByEmail(request.getEmail()).ifPresent(u -> {
            throw new AppException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email already exists");
        });

        AppUser user = new AppUser();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        AppUser saved = appUserRepository.save(user);

        String accessToken = jwtProvider.generateAccessToken(saved);
        String refreshTokenValue = jwtProvider.generateRefreshToken(saved);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(saved);
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setExpiryDate(Instant.now().plusSeconds(jwtProvider.getRefreshTokenTtlSeconds()));
        refreshTokenRepository.save(refreshToken);

        return buildTokenResponse("Register successful", accessToken, refreshTokenValue, saved);
    }

    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Invalid refresh token"));
        if (refreshToken.isRevoked()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REVOKED", "Refresh token revoked");
        }
        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "Refresh token expired");
        }
        AppUser user = refreshToken.getUser();
        String accessToken = jwtProvider.generateAccessToken(user);

        return buildTokenResponse("Refresh successful", accessToken, refreshToken.getToken(), user);
    }

    private TokenResponse buildTokenResponse(String message,
                                             String accessToken,
                                             String refreshToken,
                                             AppUser user) {
        TokenResponse response = new TokenResponse();
        response.setSuccess(true);
        response.setMessage(message);

        TokenResponse.TokenData data = new TokenResponse.TokenData();
        data.setAccessToken(accessToken);
        data.setRefreshToken(refreshToken);
        data.setExpiresInSeconds(jwtProvider.getAccessTokenTtlSeconds());
        data.setIssuedAt(OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toString());

        TokenResponse.UserInfo userInfo = new TokenResponse.UserInfo();
        userInfo.setId(user.getId().toString());
        userInfo.setEmail(user.getEmail());
        userInfo.setRole(user.getRole().name());
        userInfo.setAvatarUrl(user.getAvatarUrl());
        data.setUser(userInfo);

        response.setData(data);
        return response;
    }
}
