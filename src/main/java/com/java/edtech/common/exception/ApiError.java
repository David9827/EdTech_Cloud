package com.java.edtech.common.exception;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    private Instant timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String userMessage;
    private String path;
    private String requestId;
    private Map<String, String> fieldErrors;
}
