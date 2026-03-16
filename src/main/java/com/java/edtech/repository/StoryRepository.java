package com.java.edtech.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.java.edtech.domain.entity.Story;
import com.java.edtech.domain.enums.StoryStatus;

public interface StoryRepository extends JpaRepository<Story, UUID> {
    Optional<Story> findFirstByTopicIdOrderByCreatedAtDesc(UUID topicId);

    Optional<Story> findByIdAndStatus(UUID id, StoryStatus status);

    List<Story> findByStatusOrderByCreatedAtDesc(StoryStatus status);

    List<Story> findByTitleStartingWithIgnoreCaseOrderByCreatedAtDesc(String title);

    List<Story> findByStatusAndTitleStartingWithIgnoreCaseOrderByCreatedAtDesc(StoryStatus status, String title);
}
