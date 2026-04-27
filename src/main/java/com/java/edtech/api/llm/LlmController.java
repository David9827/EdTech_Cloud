package com.java.edtech.api.llm;

import com.java.edtech.api.llm.dto.LlmApiResponse;
import com.java.edtech.api.llm.dto.LlmTestRequest;
import com.java.edtech.api.llm.dto.LlmTestResponse;
import com.java.edtech.llm.dto.LlmResponse;
import com.java.edtech.llm.service.LlmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {
    private static final Logger log = LoggerFactory.getLogger(LlmController.class);

    private final LlmService llmService;

    @PostMapping("/test")
    public ResponseEntity<LlmApiResponse<LlmTestResponse>> testReply(@Valid @RequestBody LlmTestRequest request) {
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        long start = System.currentTimeMillis();
        log.info("API llmTest questionLength={}", question.length());

        LlmResponse llmResponse = llmService.generateReply(question);
        long elapsedMs = System.currentTimeMillis() - start;

        LlmTestResponse data = LlmTestResponse.builder()
                .question(question)
                .answer(llmResponse.getText())
                .llmSuccess(llmResponse.isSuccess())
                .provider(llmResponse.getProvider())
                .model(llmResponse.getModel())
                .error(llmResponse.getError())
                .elapsedMs(elapsedMs)
                .build();

        log.info("API llmTest success={} elapsedMs={} provider={} model={} error={}",
                data.isLlmSuccess(), data.getElapsedMs(), data.getProvider(), data.getModel(), data.getError());

        return ResponseEntity.ok(LlmApiResponse.ok("LLM test completed", data));
    }
}

