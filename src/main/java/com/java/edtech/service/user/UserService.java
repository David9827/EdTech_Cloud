package com.java.edtech.service.user;

import java.util.UUID;

import com.java.edtech.api.user.dto.ChangePasswordRequest;
import com.java.edtech.api.user.dto.DeleteAccountRequest;
import com.java.edtech.api.user.dto.UpdateAvatarRequest;
import com.java.edtech.api.user.dto.UserProfileResponse;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.domain.entity.AppUser;
import com.java.edtech.repository.AppUserRepository;
import com.java.edtech.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "New password and confirm password do not match");
        }

        AppUser user = getCurrentUserOrThrow();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        appUserRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    @Transactional
    public void deleteAccount(DeleteAccountRequest request) {
        AppUser user = getCurrentUserOrThrow();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
        }

        user.setActive(false);
        appUserRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile() {
        AppUser user = getCurrentUserOrThrow();
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId().toString());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setRole(user.getRole() == null ? null : user.getRole().name());
        response.setActive(user.isActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

    @Transactional
    public void updateAvatar(UpdateAvatarRequest request) {
        AppUser user = getCurrentUserOrThrow();
        user.setAvatarUrl(request.getAvatarUrl());
        appUserRepository.save(user);
    }

    private AppUser getCurrentUserOrThrow() {
        UUID userId = getCurrentUserId();
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        if (!user.isActive()) {
            throw new AppException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "User is inactive");
        }
        return user;
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
        }

        try {
            return UUID.fromString(authentication.getPrincipal().toString());
        } catch (IllegalArgumentException ex) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid token");
        }
    }
}
