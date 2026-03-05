package com.java.edtech.api.conversation.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonPropertyOrder({"success", "message", "data"})
public class ConversationApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ConversationApiResponse<T> ok(String message, T data) {
        ConversationApiResponse<T> response = new ConversationApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
}
