package com.java.edtech.api.conversation.dto;

import com.java.edtech.domain.enums.EmotionType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RobotChatRequest {
    @NotBlank(message = "message is required")
    private String message;

    private EmotionType emotion;
}
