package com.java.edtech.api.conversation.dto;

import java.time.Instant;
import java.util.UUID;

import com.java.edtech.domain.enums.EmotionType;
import com.java.edtech.domain.enums.MessageRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MessageResponse {
    private UUID id;
    private UUID sessionId;
    private MessageRole role;
    private String content;
    private EmotionType emotion;
    private Instant createdAt;
}
