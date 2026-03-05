package com.java.edtech.api.conversation.dto;

import java.time.Instant;
import java.util.UUID;

import com.java.edtech.domain.enums.RobotStatus;
import com.java.edtech.domain.enums.StoryStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConversationSessionResponse {
    private UUID id;
    private UUID robotId;
    private RobotStatus robotStatus;
    private UUID childId;
    private UUID topicId;
    private StoryStatus storyStatus;
    private Instant startedAt;
    private Instant endedAt;
}
