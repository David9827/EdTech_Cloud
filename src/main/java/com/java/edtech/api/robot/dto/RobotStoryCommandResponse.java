package com.java.edtech.api.robot.dto;

import java.time.Instant;
import java.util.UUID;

import com.java.edtech.domain.enums.RobotCommandType;
import com.java.edtech.domain.enums.RobotStoryCommandAction;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RobotStoryCommandResponse {
    private UUID robotId;
    private String commandId;
    private RobotCommandType type;
    private RobotStoryCommandAction action;
    private UUID storyId;
    private Instant issuedAt;
    private Instant expiresAt;
}
