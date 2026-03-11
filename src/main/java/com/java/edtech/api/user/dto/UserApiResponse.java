package com.java.edtech.api.user.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonPropertyOrder({"success", "message", "data"})
public class UserApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> UserApiResponse<T> ok(String message, T data) {
        UserApiResponse<T> response = new UserApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
}
