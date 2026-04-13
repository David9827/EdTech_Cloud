package com.java.edtech.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.edtech.common.exception.ApiError;
import com.java.edtech.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        ErrorCode code = ErrorCode.ACCESS_DENIED;
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
                accessDeniedException.getMessage());
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
