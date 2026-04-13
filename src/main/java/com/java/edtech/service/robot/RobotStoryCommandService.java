package com.java.edtech.service.robot;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.edtech.api.robot.dto.IssueStoryCommandRequest;
import com.java.edtech.api.robot.dto.RobotStoryCommandResponse;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.common.exception.ErrorCode;
import com.java.edtech.domain.entity.Robot;
import com.java.edtech.domain.entity.Story;
import com.java.edtech.domain.enums.RobotCommandType;
import com.java.edtech.domain.enums.RobotStoryCommandAction;
import com.java.edtech.domain.enums.StoryStatus;
import com.java.edtech.repository.RobotRepository;
import com.java.edtech.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RobotStoryCommandService {
    private static final Duration COMMAND_TTL = Duration.ofSeconds(60);
    private static final String REDIS_COMMAND_STACK_KEY_PREFIX = "robot:command:stack:";

    private final RobotRepository robotRepository;
    private final StoryRepository storyRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RobotStoryCommandResponse issueCommand(UUID robotId, IssueStoryCommandRequest request) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new AppException(ErrorCode.ROBOT_NOT_FOUND));

        RobotCommandType commandType = resolveCommandType(request);
        UUID storyId = validateAndResolveStoryId(commandType, request.getStoryId());
        UUID reminderId = validateAndResolveReminderId(commandType, request.getReminderId());
        RobotStoryCommandAction legacyAction = resolveLegacyAction(commandType);

        Instant now = Instant.now();
        RobotStoryCommand command = new RobotStoryCommand(
                UUID.randomUUID().toString(),
                robot.getId(),
                commandType,
                legacyAction,
                storyId,
                reminderId,
                now,
                now.plus(COMMAND_TTL)
        );

        pushToRedis(robotId, command);
        return toResponse(command);
    }

    public RobotStoryCommandResponse pollCommand(UUID robotId, boolean consume) {
        robotRepository.findById(robotId)
                .orElseThrow(() -> new AppException(ErrorCode.ROBOT_NOT_FOUND));

        RobotStoryCommand command = readNextValidCommand(robotId, consume);
        if (command == null) {
            return null;
        }
        return toResponse(command);
    }

    private RobotCommandType resolveCommandType(IssueStoryCommandRequest request) {
        if (request.getType() != null) {
            return request.getType();
        }
        if (request.getAction() == null) {
            throw new AppException(ErrorCode.COMMAND_TYPE_REQUIRED);
        }
        return switch (request.getAction()) {
            case START -> RobotCommandType.START_STORY;
            case STOP -> RobotCommandType.STOP_STORY;
        };
    }

    private UUID validateAndResolveStoryId(RobotCommandType commandType, UUID storyId) {
        if (commandType != RobotCommandType.START_STORY) {
            return storyId;
        }
        if (storyId == null) {
            throw new AppException(ErrorCode.STORY_ID_REQUIRED);
        }
        Story story = storyRepository.findByIdAndStatus(storyId, StoryStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(ErrorCode.STORY_NOT_FOUND));
        return story.getId();
    }

    private UUID validateAndResolveReminderId(RobotCommandType commandType, UUID reminderId) {
        if ((commandType == RobotCommandType.REMINDER_CREATE || commandType == RobotCommandType.REMINDER_CANCEL)
                && reminderId == null) {
            throw new AppException(ErrorCode.REMINDER_ID_REQUIRED);
        }
        return reminderId;
    }

    private RobotStoryCommandAction resolveLegacyAction(RobotCommandType commandType) {
        return switch (commandType) {
            case START_STORY -> RobotStoryCommandAction.START;
            case STOP_STORY -> RobotStoryCommandAction.STOP;
            default -> null;
        };
    }

    private void pushToRedis(UUID robotId, RobotStoryCommand command) {
        try {
            String payload = objectMapper.writeValueAsString(command);
            stringRedisTemplate.opsForList().leftPush(commandStackKey(robotId), payload);
            stringRedisTemplate.expire(commandStackKey(robotId), COMMAND_TTL.multipliedBy(2));
        } catch (Exception ex) {
            throw new AppException(ErrorCode.REDIS_WRITE_ERROR);
        }
    }

    private RobotStoryCommand readNextValidCommand(UUID robotId, boolean consume) {
        try {
            while (true) {
                String payload = consume
                        ? stringRedisTemplate.opsForList().leftPop(commandStackKey(robotId))
                        : stringRedisTemplate.opsForList().index(commandStackKey(robotId), 0);
                if (payload == null || payload.isBlank()) {
                    return null;
                }
                RobotStoryCommand command = objectMapper.readValue(payload, RobotStoryCommand.class);
                if (!isExpired(command)) {
                    return command;
                }
                if (!consume) {
                    stringRedisTemplate.opsForList().leftPop(commandStackKey(robotId));
                }
            }
        } catch (Exception ex) {
            throw new AppException(ErrorCode.REDIS_READ_ERROR);
        }
    }

    private boolean isExpired(RobotStoryCommand command) {
        return command.expiresAt() != null && command.expiresAt().isBefore(Instant.now());
    }

    private RobotStoryCommandResponse toResponse(RobotStoryCommand command) {
        return RobotStoryCommandResponse.builder()
                .robotId(command.robotId())
                .commandId(command.commandId())
                .type(command.type())
                .action(command.action())
                .storyId(command.storyId())
                .issuedAt(command.issuedAt())
                .expiresAt(command.expiresAt())
                .build();
    }

    private String commandStackKey(UUID robotId) {
        return REDIS_COMMAND_STACK_KEY_PREFIX + robotId;
    }

    private record RobotStoryCommand(
            String commandId,
            UUID robotId,
            RobotCommandType type,
            RobotStoryCommandAction action,
            UUID storyId,
            UUID reminderId,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
