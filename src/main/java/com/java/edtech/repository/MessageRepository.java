package com.java.edtech.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
            where m.robot.id = :robotId
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findFirstPageByRobotId(
            @Param("robotId") UUID robotId,
            Pageable pageable
    );

    @Query("""
            select m
            from Message m
            where m.robot.id = :robotId
              and (
                    m.createdAt < :cursorCreatedAt
                    or (m.createdAt = :cursorCreatedAt and m.id < :cursorId)
              )
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findNextPageByRobotIdBeforeCursor(
            @Param("robotId") UUID robotId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}
