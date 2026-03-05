package com.java.edtech.api.conversation.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateConversationSessionRequest {
    @NotNull(message = "robotId is required")
    private UUID robotId;
    private UUID childId;
    private UUID topicId;
}
