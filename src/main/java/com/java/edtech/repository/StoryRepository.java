package com.java.edtech.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.java.edtech.domain.entity.Story;
import com.java.edtech.domain.enums.StoryStatus;

public interface StoryRepository extends JpaRepository<Story, UUID> {
    Optional<Story> findFirstByTopicIdOrderByCreatedAtDesc(UUID topicId);

    Optional<Story> findByIdAndStatus(UUID id, StoryStatus status);
}
