package com.java.edtech.api.llm.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LlmTestResponse {
    private String question;
    private String answer;
    private boolean llmSuccess;
    private String provider;
    private String model;
    private String error;
    private long elapsedMs;
}

