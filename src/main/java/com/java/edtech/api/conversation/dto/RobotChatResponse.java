package com.java.edtech.api.conversation.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RobotChatResponse {
    private UUID sessionId;
    private UUID robotId;
    private MessageResponse userMessage;
    private MessageResponse assistantMessage;
    private String provider;
    private String model;
}
