package com.java.edtech.api.story.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonPropertyOrder({"success", "message", "data"})
public class StoryApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> StoryApiResponse<T> ok(String message, T data) {
        StoryApiResponse<T> response = new StoryApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
}
