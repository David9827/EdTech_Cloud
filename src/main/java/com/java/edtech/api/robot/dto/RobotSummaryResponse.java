package com.java.edtech.api.robot.dto;

import java.time.Instant;
import java.util.UUID;

import com.java.edtech.domain.enums.RobotStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RobotSummaryResponse {
    private UUID id;
    private String name;
    private String imgUrl;
    private RobotStatus status;
    private Integer volume;
    private Instant createdAt;
}
