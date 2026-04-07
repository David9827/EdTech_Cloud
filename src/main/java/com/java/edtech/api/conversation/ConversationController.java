package com.java.edtech.api.conversation;

import java.util.List;
import java.util.UUID;

import com.java.edtech.api.conversation.dto.ConversationApiResponse;
import com.java.edtech.api.conversation.dto.ConversationSessionPageResponse;
import com.java.edtech.api.conversation.dto.ConversationSessionResponse;
import com.java.edtech.api.conversation.dto.CreateConversationSessionRequest;
import com.java.edtech.api.conversation.dto.CreateMessageRequest;
import com.java.edtech.api.conversation.dto.MessageCursorResponse;
import com.java.edtech.api.conversation.dto.MessageResponse;
import com.java.edtech.service.conversation.ConversationService;
import com.java.edtech.service.conversation.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationService conversationService;
    private final MessageService messageService;

    @PostMapping("/sessions")
    public ResponseEntity<ConversationApiResponse<ConversationSessionResponse>> createSession(
            @Valid @RequestBody CreateConversationSessionRequest request
    ) {
        log.info("API createSession robotId={} childId={} topicId={}",
                request.getRobotId(), request.getChildId(), request.getTopicId());
        ConversationSessionResponse response = conversationService.createSession(request);
        log.info("API createSession success sessionId={}", response.getId());
        return ResponseEntity.ok(ConversationApiResponse.ok("Conversation session created successfully", response));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ConversationApiResponse<ConversationSessionResponse>> getSession(@PathVariable UUID sessionId) {
        log.info("API getSession sessionId={}", sessionId);
        ConversationSessionResponse response = conversationService.getSession(sessionId);
        log.info("API getSession success sessionId={} startedAt={} endedAt={}",
                response.getId(), response.getStartedAt(), response.getEndedAt());
        return ResponseEntity.ok(ConversationApiResponse.ok("Conversation session fetched successfully", response));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ConversationApiResponse<ConversationSessionPageResponse>> listSessions(
            @RequestParam(required = false) UUID robotId,
            @RequestParam(required = false) UUID childId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("API listSessions robotId={} childId={} page={} size={}", robotId, childId, page, size);
        ConversationSessionPageResponse response = conversationService.listSessions(robotId, childId, page, size);
        log.info("API listSessions success count={} total={} page={}/{}",
                response.getItems().size(), response.getTotalElements(), response.getPage(), response.getTotalPages());
        return ResponseEntity.ok(ConversationApiResponse.ok("Conversation sessions fetched successfully", response));
    }

    @PatchMapping("/sessions/{sessionId}/end")
    public ResponseEntity<ConversationApiResponse<ConversationSessionResponse>> endSession(@PathVariable UUID sessionId) {
        log.info("API endSession sessionId={}", sessionId);
        ConversationSessionResponse response = conversationService.endSession(sessionId);
        log.info("API endSession success sessionId={} endedAt={}", response.getId(), response.getEndedAt());
        return ResponseEntity.ok(ConversationApiResponse.ok("Conversation session ended successfully", response));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<MessageResponse> addMessage(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CreateMessageRequest request
    ) {
        log.info("API addMessage sessionId={} role={} contentLength={}",
                sessionId, request.getRole(), request.getContent() == null ? 0 : request.getContent().length());
        MessageResponse response = messageService.addMessage(sessionId, request);
        log.info("API addMessage success messageId={} sessionId={} role={}",
                response.getId(), response.getSessionId(), response.getRole());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ConversationApiResponse<List<MessageResponse>>> getMessages(@PathVariable UUID sessionId) {
        log.info("API getMessages sessionId={}", sessionId);
        List<MessageResponse> response = messageService.getSessionMessages(sessionId);
        log.info("API getMessages success sessionId={} count={}", sessionId, response.size());
        return ResponseEntity.ok(ConversationApiResponse.ok("Conversation messages fetched successfully", response));
    }

    @GetMapping("/messages")
    public ResponseEntity<MessageCursorResponse> listMessagesByRobot(
            @RequestParam UUID robotId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.info("API listMessagesByRobot robotId={} cursor={} limit={}", robotId, cursor, limit);
        MessageCursorResponse response = messageService.listMessagesByRobot(robotId, cursor, limit);
        log.info("API listMessagesByRobot success robotId={} count={} hasMore={} nextCursor={}",
                robotId, response.getItems().size(), response.isHasMore(), response.getNextCursor());
        return ResponseEntity.ok(response);
    }
}
