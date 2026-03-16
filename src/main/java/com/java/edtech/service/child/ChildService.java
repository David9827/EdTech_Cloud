package com.java.edtech.service.child;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

import com.java.edtech.api.child.dto.ChildResponse;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.domain.entity.AppUser;
import com.java.edtech.domain.entity.Child;
import com.java.edtech.repository.AppUserRepository;
import com.java.edtech.repository.ChildRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChildService {
    private final ChildRepository childRepository;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public List<ChildResponse> getMyChildren() {
        AppUser user = getCurrentUserOrThrow();
        List<Child> children = childRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return children.stream().map(this::toResponse).toList();
    }

    private ChildResponse toResponse(Child child) {
        return ChildResponse.builder()
                .id(child.getId())
                .name(child.getName())
                .age(resolveAge(child.getBirthDate()))
                .avatarUrl(child.getAvatarUrl())
                .build();
    }

    private Integer resolveAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
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
