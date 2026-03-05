package com.java.edtech.api.conversation.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConversationSessionResponse {
    private UUID id;
    private UUID robotId;
    private UUID childId;
    private UUID topicId;
    private Instant startedAt;
    private Instant endedAt;
}
