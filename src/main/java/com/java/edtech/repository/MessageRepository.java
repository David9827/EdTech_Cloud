package com.java.edtech.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.java.edtech.domain.entity.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
