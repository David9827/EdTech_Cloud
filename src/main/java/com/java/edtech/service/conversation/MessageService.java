package com.java.edtech.service.conversation;

import java.util.List;
import java.util.UUID;

import com.java.edtech.api.conversation.dto.CreateMessageRequest;
import com.java.edtech.api.conversation.dto.MessagePageResponse;
import com.java.edtech.api.conversation.dto.MessageResponse;
import com.java.edtech.domain.entity.ConversationSession;
import com.java.edtech.domain.entity.Message;
import com.java.edtech.domain.enums.MessageRole;
import com.java.edtech.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final ConversationService conversationService;

    @Transactional
    public MessageResponse addMessage(UUID sessionId, CreateMessageRequest request) {
        log.info("SERVICE addMessage sessionId={} role={} contentLength={}",
                sessionId, request.getRole(), request.getContent() == null ? 0 : request.getContent().length());
        ConversationSession session = conversationService.getSessionEntity(sessionId);
        Message message = new Message();
        message.setSession(session);
        message.setRole(request.getRole());
        message.setContent(request.getContent().trim());
        message.setEmotion(request.getEmotion());
        Message saved = messageRepository.save(message);
        log.info("SERVICE addMessage saved messageId={} sessionId={}", saved.getId(), sessionId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getSessionMessages(UUID sessionId) {
        conversationService.getSessionEntity(sessionId);
        List<MessageResponse> result = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toResponse)
                .toList();
        log.info("SERVICE getSessionMessages sessionId={} count={}", sessionId, result.size());
        return result;
    }

    @Transactional(readOnly = true)
    public MessagePageResponse listMessagesByRobot(UUID robotId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<Message> messages = messageRepository.findByRobotId(robotId, pageable);
        List<MessageResponse> items = messages.getContent().stream()
                .map(this::toResponse)
                .toList();
        log.info("SERVICE listMessagesByRobot robotId={} count={} page={}/{}",
                robotId, items.size(), messages.getNumber(), messages.getTotalPages());
        return MessagePageResponse.builder()
                .items(items)
                .page(messages.getNumber())
                .size(messages.getSize())
                .totalElements(messages.getTotalElements())
                .totalPages(messages.getTotalPages())
                .hasNext(messages.hasNext())
                .build();
    }

    @Transactional
    public void saveTurnFromWs(String sessionId, String robotId, String transcript, String assistantReply) {
        log.info("WS saveTurn start sessionId={} robotId={} transcriptLen={} replyLen={}",
                sessionId,
                robotId,
                transcript == null ? 0 : transcript.length(),
                assistantReply == null ? 0 : assistantReply.length());
        conversationService.ensureSessionExistsFromWs(sessionId, robotId);
        UUID sessionUuid = parseUuidOrNull(sessionId);
        if (sessionUuid == null) {
            log.warn("WS saveTurn skipped invalid sessionId={}", sessionId);
            return;
        }

        ConversationSession session = conversationService.findSessionEntity(sessionUuid);
        if (session == null) {
            log.warn("WS saveTurn skipped session not found sessionId={}", sessionUuid);
            return;
        }
        if (transcript != null && !transcript.isBlank()) {
            Message userMessage = new Message();
            userMessage.setSession(session);
            userMessage.setRole(MessageRole.USER);
            userMessage.setContent(transcript.trim());
            messageRepository.save(userMessage);
            log.info("WS saveTurn saved USER message sessionId={}", sessionUuid);
        }
        if (assistantReply != null && !assistantReply.isBlank()) {
            Message assistantMessage = new Message();
            assistantMessage.setSession(session);
            assistantMessage.setRole(MessageRole.ASSISTANT);
            assistantMessage.setContent(assistantReply.trim());
            messageRepository.save(assistantMessage);
            log.info("WS saveTurn saved ASSISTANT message sessionId={}", sessionUuid);
        }
    }

    private MessageResponse toResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .sessionId(message.getSession() == null ? null : message.getSession().getId())
                .role(message.getRole())
                .content(message.getContent())
                .emotion(message.getEmotion())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
