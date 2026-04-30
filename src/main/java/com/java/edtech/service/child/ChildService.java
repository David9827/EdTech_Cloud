package com.java.edtech.service.child;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

import com.java.edtech.api.child.dto.CreateChildRequest;
import com.java.edtech.api.child.dto.ChildResponse;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.common.exception.ErrorCode;
import com.java.edtech.domain.entity.AppUser;
import com.java.edtech.domain.entity.Child;
import com.java.edtech.repository.AppUserRepository;
import com.java.edtech.repository.ChildRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    @Transactional
    public ChildResponse createMyChild(CreateChildRequest request) {
        AppUser user = getCurrentUserOrThrow();

        Child child = new Child();
        child.setId(UUID.randomUUID());
        child.setUserId(user.getId());
        child.setName(request.getName().trim());
        child.setBirthDate(request.getBirthDate());
        child.setAvatarUrl(normalizeOptional(request.getAvatarUrl()));

        Child saved = childRepository.save(child);
        return toResponse(saved);
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

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private AppUser getCurrentUserOrThrow() {
        UUID userId = getCurrentUserId();
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
        return user;
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        try {
            return UUID.fromString(authentication.getPrincipal().toString());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }
}
