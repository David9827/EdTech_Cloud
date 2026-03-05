package com.java.edtech.llm.dto;

import lombok.Data;

@Data
public class LlmRequest {
    private String systemPrompt;
    private String userPrompt;
    private String model;
    private Double temperature = 0.7;
    private Integer maxTokens = 10000;
}
