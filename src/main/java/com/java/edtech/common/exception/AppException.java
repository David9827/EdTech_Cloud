package com.java.edtech.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }

    public String getUserMessage() {
        return errorCode.getUserMessage();
    }
}
