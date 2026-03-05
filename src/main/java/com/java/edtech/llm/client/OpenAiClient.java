package com.java.edtech.llm.client;

import java.util.List;
import java.util.Map;

import com.java.edtech.llm.config.LlmProperties;
import com.java.edtech.llm.dto.LlmRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class OpenAiClient {
    private final LlmProperties llmProperties;

    public String generateText(LlmRequest request) {
        RestClient client = RestClient.builder()
                .baseUrl(trimBaseUrl(llmProperties.getBaseUrl()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + llmProperties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> payload = Map.of(
                "model", request.getModel(),
                "temperature", request.getTemperature(),
                "max_tokens", request.getMaxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", request.getSystemPrompt()),
                        Map.of("role", "user", "content", request.getUserPrompt())
                )
        );

        Map<?, ?> response = client.post()
                .uri("/chat/completions")
                .body(payload)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return null;
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        Object firstObj = choices.get(0);
        if (!(firstObj instanceof Map<?, ?> first)) {
            return null;
        }
        Object messageObj = first.get("message");
        if (!(messageObj instanceof Map<?, ?> message)) {
            return null;
        }
        Object content = message.get("content");
        return content == null ? null : content.toString();
    }

    private String trimBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("llm.base-url is required when llm.enabled=true");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
