package com.java.edtech.api.llm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonPropertyOrder({"success", "message", "data"})
public class LlmApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> LlmApiResponse<T> ok(String message, T data) {
        LlmApiResponse<T> response = new LlmApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
}

