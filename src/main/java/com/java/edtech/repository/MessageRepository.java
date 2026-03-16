package com.java.edtech.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.java.edtech.domain.entity.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    @Query("""
            select m
            from Message m
            join m.session s
            where s.robot.id = :robotId
            """)
    Page<Message> findByRobotId(@Param("robotId") UUID robotId, Pageable pageable);
}
