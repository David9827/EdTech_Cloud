package com.java.edtech.api.llm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LlmTestRequest {
    @NotBlank(message = "question is required")
    private String question;
}

