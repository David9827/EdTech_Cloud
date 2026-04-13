package com.java.edtech.common.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleAppException(AppException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ApiError body = buildError(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getUserMessage(), request, requestId, null);
        logByStatus(ex.getStatus(), request, requestId, ex.getCode(), ex.getMessage(), ex);
        return buildResponse(ex.getStatus(), requestId, body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(err.getField(), err.getDefaultMessage());
        }
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        ApiError body = buildError(code.getStatus(), code.getCode(), code.getMessage(), code.getUserMessage(),
                request, requestId, fieldErrors);
        logByStatus(code.getStatus(), request, requestId, code.getCode(), code.getMessage(), null);
        return buildResponse(code.getStatus(), requestId, body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ErrorCode code = ErrorCode.REQUEST_BODY_INVALID;
        ApiError body = buildError(code.getStatus(), code.getCode(), code.getMessage(), code.getUserMessage(),
                request, requestId, null);
        logByStatus(code.getStatus(), request, requestId, code.getCode(), ex.getMessage(), null);
        return buildResponse(code.getStatus(), requestId, body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ErrorCode code = ErrorCode.PARAMETER_TYPE_MISMATCH;
        Map<String, String> fieldErrors = new HashMap<>();
        fieldErrors.put(ex.getName(), "Expected type: " + resolveRequiredType(ex));

        ApiError body = buildError(code.getStatus(), code.getCode(), code.getMessage(), code.getUserMessage(),
                request, requestId, fieldErrors);
        logByStatus(code.getStatus(), request, requestId, code.getCode(), ex.getMessage(), null);
        return buildResponse(code.getStatus(), requestId, body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ErrorCode code = ErrorCode.CONSTRAINT_VIOLATION;
        Map<String, String> fieldErrors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            fieldErrors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        ApiError body = buildError(code.getStatus(), code.getCode(), code.getMessage(), code.getUserMessage(),
                request, requestId, fieldErrors);
        logByStatus(code.getStatus(), request, requestId, code.getCode(), ex.getMessage(), null);
        return buildResponse(code.getStatus(), requestId, body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ErrorCode code = ErrorCode.DATA_INTEGRITY_VIOLATION;
        ApiError body = buildError(code.getStatus(), code.getCode(), code.getMessage(), code.getUserMessage(),
                request, requestId, null);
        logByStatus(code.getStatus(), request, requestId, code.getCode(), ex.getMessage(), null);
        return buildResponse(code.getStatus(), requestId, body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        ApiError body = buildError(code.getStatus(), code.getCode(), code.getMessage(), code.getUserMessage(),
                request, requestId, null);
        logByStatus(code.getStatus(), request, requestId, code.getCode(), ex.getMessage(), ex);
        return buildResponse(code.getStatus(), requestId, body);
    }

    private ApiError buildError(HttpStatus status,
                                String code,
                                String message,
                                String userMessage,
                                HttpServletRequest request,
                                String requestId,
                                Map<String, String> fieldErrors) {
        return ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .userMessage(userMessage)
                .path(request.getRequestURI())
                .requestId(requestId)
                .fieldErrors(fieldErrors)
                .build();
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String requestId, ApiError body) {
        return ResponseEntity.status(status)
                .header(REQUEST_ID_HEADER, requestId)
                .body(body);
    }

    private String resolveRequestId(HttpServletRequest request) {
        String existingRequestId = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(existingRequestId)) {
            return existingRequestId;
        }
        return UUID.randomUUID().toString();
    }

    private String resolveRequiredType(MethodArgumentTypeMismatchException ex) {
        if (ex.getRequiredType() == null) {
            return "unknown";
        }
        return ex.getRequiredType().getSimpleName();
    }

    private void logByStatus(HttpStatus status,
                             HttpServletRequest request,
                             String requestId,
                             String code,
                             String detail,
                             Exception ex) {
        if (status.is5xxServerError()) {
            log.error("requestId={} {} {} failed with code={} detail={}",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    code,
                    detail,
                    ex);
            return;
        }

        log.warn("requestId={} {} {} failed with code={} detail={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                code,
                detail);
    }
}
