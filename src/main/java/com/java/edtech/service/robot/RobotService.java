package com.java.edtech.service.robot;

import java.util.List;
import java.util.UUID;

import com.java.edtech.api.robot.dto.CreateRobotRequest;
import com.java.edtech.api.robot.dto.RobotSummaryResponse;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.common.exception.ErrorCode;
import com.java.edtech.domain.entity.AppUser;
import com.java.edtech.domain.entity.Robot;
import com.java.edtech.domain.enums.RobotStatus;
import com.java.edtech.repository.AppUserRepository;
import com.java.edtech.repository.RobotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    @Transactional
    public RobotSummaryResponse createMyRobot(CreateRobotRequest request) {
        AppUser user = getCurrentUserOrThrow();

        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setName(request.getName().trim());
        robot.setDeviceKey(request.getDeviceKey().trim());
        robot.setImgUrl(normalizeOptional(request.getImgUrl()));
        robot.setStatus(RobotStatus.IDLE);
        robot.setVolume(request.getVolume() == null ? 50 : request.getVolume());

        Robot saved = robotRepository.save(robot);
        robotRepository.addUserRobot(user.getId(), saved.getId());
        return toSummaryResponse(saved);
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
