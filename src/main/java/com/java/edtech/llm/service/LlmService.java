package com.java.edtech.llm.service;

import com.java.edtech.llm.client.OpenAiClient;
import com.java.edtech.llm.config.LlmProperties;
import com.java.edtech.llm.dto.LlmRequest;
import com.java.edtech.llm.dto.LlmResponse;
import com.java.edtech.llm.prompt.PromptBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmService {
    private final OpenAiClient openAiClient;
    private final PromptBuilder promptBuilder;
    private final LlmProperties llmProperties;

    public LlmResponse generateReply(String transcript) {
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
        request.setSystemPrompt(promptBuilder.buildSystemPrompt());
        request.setUserPrompt(promptBuilder.buildUserPrompt(transcript));

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
            response.setSuccess(false);
            response.setError(ex.getMessage());
            return response;
        }
    }
}
