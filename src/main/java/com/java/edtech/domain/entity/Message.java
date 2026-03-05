package com.java.edtech.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.java.edtech.domain.enums.EmotionType;
import com.java.edtech.domain.enums.MessageRole;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "message")
@Getter
@Setter
public class Message {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ConversationSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "message_role")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "emotion_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EmotionType emotion;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
