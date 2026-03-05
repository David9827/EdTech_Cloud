package com.java.edtech.llm.dto;

import lombok.Data;

@Data
public class LlmResponse {
    private boolean success;
    private String provider;
    private String model;
    private String text;
    private String error;
}
