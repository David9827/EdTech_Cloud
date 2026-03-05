package com.java.edtech.service.conversation;

import java.time.Instant;
import java.util.UUID;

import com.java.edtech.api.conversation.dto.ConversationSessionResponse;
import com.java.edtech.api.conversation.dto.CreateConversationSessionRequest;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.domain.entity.Child;
import com.java.edtech.domain.entity.ConversationSession;
import com.java.edtech.domain.entity.Robot;
import com.java.edtech.repository.ChildRepository;
import com.java.edtech.repository.ConversationSessionRepository;
import com.java.edtech.repository.RobotRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationSessionRepository conversationSessionRepository;
    private final RobotRepository robotRepository;
    private final ChildRepository childRepository;

    @Transactional
    public ConversationSessionResponse createSession(CreateConversationSessionRequest request) {
        log.info("SERVICE createSession robotId={} childId={} topicId={}",
                request.getRobotId(), request.getChildId(), request.getTopicId());
        Robot robot = robotRepository.findById(request.getRobotId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ROBOT_NOT_FOUND", "Robot not found"));

        ConversationSession session = new ConversationSession();
        session.setId(UUID.randomUUID());
        session.setRobot(robot);
        session.setTopicId(request.getTopicId());

        if (request.getChildId() != null) {
            Child child = childRepository.findById(request.getChildId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CHILD_NOT_FOUND", "Child not found"));
            session.setChild(child);
        }

        ConversationSession saved = conversationSessionRepository.save(session);
        log.info("SERVICE createSession saved sessionId={}", saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ConversationSession getSessionEntity(UUID sessionId) {
        return conversationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Conversation session not found"));
    }

    @Transactional(readOnly = true)
    public ConversationSessionResponse getSession(UUID sessionId) {
        return toResponse(getSessionEntity(sessionId));
    }

    @Transactional(readOnly = true)
    public ConversationSession findSessionEntity(UUID sessionId) {
        return conversationSessionRepository.findById(sessionId).orElse(null);
    }

    @Transactional
    public ConversationSessionResponse endSession(UUID sessionId) {
        ConversationSession session = getSessionEntity(sessionId);
        session.setEndedAt(Instant.now());
        return toResponse(conversationSessionRepository.save(session));
    }

    @Transactional
    public void ensureSessionExistsFromWs(String sessionId, String robotId) {
        UUID sessionUuid = parseUuidOrNull(sessionId);
        UUID robotUuid = parseUuidOrNull(robotId);
        if (sessionUuid == null || robotUuid == null) {
            log.warn("WS ensureSessionExists skipped invalid IDs sessionId={} robotId={}", sessionId, robotId);
            return;
        }
        if (conversationSessionRepository.existsById(sessionUuid)) {
            log.info("WS ensureSessionExists found existing sessionId={}", sessionUuid);
            return;
        }

        Robot robot = robotRepository.findById(robotUuid).orElse(null);
        if (robot == null) {
            log.warn("WS ensureSessionExists skipped robot not found robotId={}", robotUuid);
            return;
        }

        ConversationSession session = new ConversationSession();
        session.setId(sessionUuid);
        session.setRobot(robot);
        conversationSessionRepository.save(session);
        log.info("WS ensureSessionExists created sessionId={} robotId={}", sessionUuid, robotUuid);
    }

    private ConversationSessionResponse toResponse(ConversationSession session) {
        return ConversationSessionResponse.builder()
                .id(session.getId())
                .robotId(session.getRobot() == null ? null : session.getRobot().getId())
                .childId(session.getChild() == null ? null : session.getChild().getId())
                .topicId(session.getTopicId())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
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
