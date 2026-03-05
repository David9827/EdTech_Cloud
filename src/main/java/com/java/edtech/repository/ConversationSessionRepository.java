package com.java.edtech.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.java.edtech.domain.entity.ConversationSession;

public interface ConversationSessionRepository extends JpaRepository<ConversationSession, UUID> {
}
