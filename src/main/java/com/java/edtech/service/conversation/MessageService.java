package com.java.edtech.service.conversation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.java.edtech.api.conversation.dto.CreateMessageRequest;
import com.java.edtech.api.conversation.dto.MessageCursorResponse;
import com.java.edtech.api.conversation.dto.MessageResponse;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.common.exception.ErrorCode;
import com.java.edtech.domain.entity.ConversationSession;
import com.java.edtech.domain.entity.Message;
import com.java.edtech.domain.enums.MessageRole;
import com.java.edtech.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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
        message.setRobot(session.getRobot());
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
    public MessageCursorResponse listMessagesByRobot(UUID robotId, String cursor, int limit) {
        CursorKey cursorKey = parseCursor(cursor);
        int safeLimit = Math.min(100, Math.max(1, limit));
        PageRequest pageable = PageRequest.of(0, safeLimit + 1);

        List<Message> rows = cursorKey == null
                ? messageRepository.findFirstPageByRobotId(robotId, pageable)
                : messageRepository.findNextPageByRobotIdBeforeCursor(
                        robotId,
                        cursorKey.createdAt(),
                        cursorKey.id(),
                        pageable
                );

        boolean hasMore = rows.size() > safeLimit;
        List<Message> currentPage = hasMore ? rows.subList(0, safeLimit) : rows;
        List<MessageResponse> items = currentPage.stream()
                .map(this::toResponse)
                .toList();

        String nextCursor = null;
        if (hasMore && !currentPage.isEmpty()) {
            Message last = currentPage.get(currentPage.size() - 1);
            nextCursor = encodeCursor(last.getCreatedAt(), last.getId());
        }

        log.info("SERVICE listMessagesByRobot robotId={} count={} hasMore={} cursorPresent={}",
                robotId, items.size(), hasMore, cursor != null && !cursor.isBlank());
        return MessageCursorResponse.builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
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
            userMessage.setRobot(session.getRobot());
            userMessage.setRole(MessageRole.USER);
            userMessage.setContent(transcript.trim());
            messageRepository.save(userMessage);
            log.info("WS saveTurn saved USER message sessionId={}", sessionUuid);
        }
        if (assistantReply != null && !assistantReply.isBlank()) {
            Message assistantMessage = new Message();
            assistantMessage.setSession(session);
            assistantMessage.setRobot(session.getRobot());
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

    private CursorKey parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(cursor);
            String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
            Instant createdAt = Instant.parse(parts[0]);
            UUID id = UUID.fromString(parts[1]);
            return new CursorKey(createdAt, id);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_CURSOR);
        }
    }

    private String encodeCursor(Instant createdAt, UUID id) {
        if (createdAt == null || id == null) {
            return null;
        }
        String raw = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private record CursorKey(Instant createdAt, UUID id) {
    }
}
