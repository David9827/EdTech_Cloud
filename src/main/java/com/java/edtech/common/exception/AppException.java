package com.java.edtech.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public AppException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
