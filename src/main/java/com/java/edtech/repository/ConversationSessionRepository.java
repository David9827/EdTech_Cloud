package com.java.edtech.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.java.edtech.domain.entity.ConversationSession;

public interface ConversationSessionRepository extends JpaRepository<ConversationSession, UUID> {
    @Query("""
            select cs from ConversationSession cs
            where (:robotId is null or cs.robot.id = :robotId)
              and (:childId is null or cs.child.id = :childId)
            order by cs.startedAt desc
            """)
    Page<ConversationSession> findByFilters(
            @Param("robotId") UUID robotId,
            @Param("childId") UUID childId,
            Pageable pageable
    );
}
