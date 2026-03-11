package com.java.edtech.llm.service;

import com.java.edtech.llm.client.OpenAiClient;
import com.java.edtech.llm.config.LlmProperties;
import com.java.edtech.llm.dto.LlmRequest;
import com.java.edtech.llm.dto.LlmResponse;
import com.java.edtech.llm.prompt.LlmMode;
import com.java.edtech.llm.prompt.PromptBuilder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmService {
    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final OpenAiClient openAiClient;
    private final PromptBuilder promptBuilder;
    private final LlmProperties llmProperties;

    public LlmResponse generateReply(String transcript) {
        return generateWithMode(LlmMode.CONVERSATION, transcript);
    }

    public LlmResponse generateStoryReply(String storyInput) {
        return generateWithMode(LlmMode.STORY, storyInput);
    }

    public LlmResponse generateStoryQaReply(String storyQaInput) {
        return generateWithMode(LlmMode.STORY_QA, storyQaInput);
    }

    private LlmResponse generateWithMode(LlmMode mode, String input) {
        LlmResponse response = new LlmResponse();
        response.setProvider(llmProperties.getProvider());
        response.setModel(llmProperties.getModel());

        if (!llmProperties.isEnabled()) {
            response.setSuccess(false);
            response.setError("LLM is disabled. Set llm.enabled=true and provide llm.api-key.");
            return response;
        }

        LlmRequest request = new LlmRequest();
        request.setModel(llmProperties.getModel());
        request.setSystemPrompt(promptBuilder.buildSystemPrompt(mode));
        request.setUserPrompt(promptBuilder.buildUserPrompt(mode, input == null ? "" : input));

        int maxAttempts = Math.max(1, llmProperties.getMaxRetries() + 1);
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String text = openAiClient.generateText(request);
                if (text == null || text.isBlank()) {
                    response.setSuccess(false);
                    response.setError("Empty response from LLM");
                    return response;
                }
                response.setSuccess(true);
                response.setText(text.trim());
                return response;
            } catch (Exception ex) {
                lastException = ex;
                boolean retryable = isRetryable(ex);
                if (!retryable || attempt >= maxAttempts) {
                    break;
                }
                long backoff = Math.max(0L, llmProperties.getRetryBackoffMs()) * attempt;
                log.warn("LLM call failed (attempt {}/{}), retrying in {}ms: {}",
                        attempt, maxAttempts, backoff, ex.getMessage());
                sleep(backoff);
            }
        }

        String fallback = (mode == LlmMode.STORY || mode == LlmMode.STORY_QA)
                ? llmProperties.getStoryFallbackMessage()
                : llmProperties.getFallbackMessage();

        if (fallback != null && !fallback.isBlank()) {
            log.warn("LLM failed after retries, using fallback message. mode={} error={}",
                    mode, lastException == null ? "unknown" : lastException.getMessage());
            response.setSuccess(true);
            response.setText(fallback.trim());
            return response;
        }

        response.setSuccess(false);
        response.setError(lastException == null ? "LLM error" : lastException.getMessage());
        return response;
    }

    private boolean isRetryable(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String msg = message.toLowerCase();
        return msg.contains("429")
                || msg.contains("503")
                || msg.contains("resource_exhausted")
                || msg.contains("unavailable")
                || msg.contains("too many requests");
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
