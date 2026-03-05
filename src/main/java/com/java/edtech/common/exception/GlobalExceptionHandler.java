package com.java.edtech.common.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleAppException(AppException ex, HttpServletRequest request) {
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(ex.getStatus().value())
                .error(ex.getStatus().getReasonPhrase())
                .code(ex.getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(err.getField(), err.getDefaultMessage());
        }
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .code("VALIDATION_ERROR")
                .message("Validation failed")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .code("INTERNAL_ERROR")
                .message("Unexpected error")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
