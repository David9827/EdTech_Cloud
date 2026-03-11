package com.java.edtech.api.reminder.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonPropertyOrder({"success", "message", "data"})
public class ReminderApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ReminderApiResponse<T> ok(String message, T data) {
        ReminderApiResponse<T> response = new ReminderApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
}
