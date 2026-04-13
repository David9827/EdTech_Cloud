package com.java.edtech.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.edtech.common.exception.ApiError;
import com.java.edtech.common.exception.ErrorCode;
import com.java.edtech.common.util.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        ErrorCode code = resolveErrorCode(request);
        String requestId = resolveRequestId(request);
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(code.getStatus().value())
                .error(code.getStatus().getReasonPhrase())
                .code(code.getCode())
                .message(code.getMessage())
                .userMessage(code.getUserMessage())
                .path(request.getRequestURI())
                .requestId(requestId)
                .build();

        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(REQUEST_ID_HEADER, requestId);
        objectMapper.writeValue(response.getWriter(), body);

        log.warn("requestId={} {} {} failed with code={} detail={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                code.getCode(),
                authException.getMessage());
    }

    private ErrorCode resolveErrorCode(HttpServletRequest request) {
        Object code = request.getAttribute(JwtAuthFilter.AUTH_ERROR_CODE_ATTR);
        if (code instanceof String rawCode) {
            try {
                return ErrorCode.valueOf(rawCode);
            } catch (IllegalArgumentException ignored) {
                return ErrorCode.UNAUTHORIZED;
            }
        }
        return ErrorCode.UNAUTHORIZED;
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
