package com.java.edtech.api.conversation.dto;

import com.java.edtech.domain.enums.EmotionType;
import com.java.edtech.domain.enums.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMessageRequest {
    @NotNull(message = "role is required")
    private MessageRole role;

    @NotBlank(message = "content is required")
    private String content;

    private EmotionType emotion;
}
