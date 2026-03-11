package com.java.edtech.api.robot.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonPropertyOrder({"success", "message", "data"})
public class RobotApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> RobotApiResponse<T> ok(String message, T data) {
        RobotApiResponse<T> response = new RobotApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
}
