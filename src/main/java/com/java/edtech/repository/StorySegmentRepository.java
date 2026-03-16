package com.java.edtech.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.java.edtech.domain.entity.StorySegment;

public interface StorySegmentRepository extends JpaRepository<StorySegment, UUID> {
    List<StorySegment> findByStoryIdOrderBySegmentOrderAsc(UUID storyId);

    void deleteByStoryId(UUID storyId);

    Optional<StorySegment> findFirstByStoryIdOrderBySegmentOrderAsc(UUID storyId);

    Optional<StorySegment> findFirstByStoryIdAndSegmentOrderGreaterThanOrderBySegmentOrderAsc(
            UUID storyId,
            Integer segmentOrder
    );
}
