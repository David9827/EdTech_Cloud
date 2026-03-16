package com.java.edtech.service.robot;

import java.util.List;
import java.util.UUID;

import com.java.edtech.api.robot.dto.RobotSummaryResponse;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.domain.entity.AppUser;
import com.java.edtech.domain.entity.Robot;
import com.java.edtech.repository.AppUserRepository;
import com.java.edtech.repository.RobotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RobotService {
    private final RobotRepository robotRepository;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public List<RobotSummaryResponse> getMyRobots() {
        AppUser user = getCurrentUserOrThrow();
        List<Robot> robots = robotRepository.findByUserId(user.getId());
        return robots.stream().map(this::toSummaryResponse).toList();
    }

    private RobotSummaryResponse toSummaryResponse(Robot robot) {
        return RobotSummaryResponse.builder()
                .id(robot.getId())
                .name(robot.getName())
                .imgUrl(robot.getImgUrl())
                .status(robot.getStatus())
                .volume(robot.getVolume())
                .createdAt(robot.getCreatedAt())
                .build();
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
