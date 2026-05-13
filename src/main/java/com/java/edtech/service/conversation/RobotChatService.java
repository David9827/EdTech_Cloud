package com.java.edtech.service.conversation;

import java.util.UUID;

import com.java.edtech.api.conversation.dto.MessageResponse;
import com.java.edtech.api.conversation.dto.RobotChatRequest;
import com.java.edtech.api.conversation.dto.RobotChatResponse;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.common.exception.ErrorCode;
import com.java.edtech.domain.entity.ConversationSession;
import com.java.edtech.domain.enums.MessageRole;
import com.java.edtech.llm.dto.LlmResponse;
import com.java.edtech.llm.service.LlmService;
import com.java.edtech.service.story.StoryQaContext;
import com.java.edtech.service.story.StoryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RobotChatService {
    private static final Logger log = LoggerFactory.getLogger(RobotChatService.class);
    private static final int STORY_QA_CONTEXT_SEGMENTS = 4;

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final LlmService llmService;
    private final StoryService storyService;

    public RobotChatResponse chat(UUID sessionId, RobotChatRequest request) {
        String userInput = request.getMessage().trim();
        log.info("SERVICE chat start sessionId={} inputLength={}", sessionId, userInput.length());

        ConversationSession session = conversationService.getSessionEntity(sessionId);
        MessageResponse userMessage = messageService.addMessage(
                session,
                MessageRole.USER,
                userInput,
                request.getEmotion()
        );

        StoryQaContext storyQaContext = storyService.getQaContext(session.getRobot().getId(), STORY_QA_CONTEXT_SEGMENTS);
        LlmResponse llmResponse = storyQaContext == null
                ? llmService.generateReply(userInput)
                : llmService.generateStoryQaReply(buildStoryQaInput(storyQaContext, userInput));

        if (!llmResponse.isSuccess() || llmResponse.getText() == null || llmResponse.getText().isBlank()) {
            String detail = llmResponse.getError() == null ? "unknown LLM error" : llmResponse.getError();
            log.warn("SERVICE chat llm failed sessionId={} detail={}", sessionId, detail);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "LLM reply failed: " + detail);
        }

        MessageResponse assistantMessage = messageService.addMessage(
                session,
                MessageRole.ASSISTANT,
                llmResponse.getText(),
                null
        );

        log.info("SERVICE chat success sessionId={} userMessageId={} assistantMessageId={} provider={} model={}",
                sessionId,
                userMessage.getId(),
                assistantMessage.getId(),
                llmResponse.getProvider(),
                llmResponse.getModel());

        return RobotChatResponse.builder()
                .sessionId(sessionId)
                .robotId(session.getRobot().getId())
                .userMessage(userMessage)
                .assistantMessage(assistantMessage)
                .provider(llmResponse.getProvider())
                .model(llmResponse.getModel())
                .build();
    }

    private String buildStoryQaInput(StoryQaContext context, String question) {
        return "Tieu de truyen: " + context.getStoryTitle()
                + "\nDang o doan: " + context.getCurrentSegmentOrder()
                + "\nCac doan da ke gan day:\n" + context.getRecentContext()
                + "\nCau hoi cua be: " + question;
    }
}
