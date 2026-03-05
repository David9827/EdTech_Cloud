package com.java.edtech.service.story;

import java.util.List;
import java.util.UUID;

import com.java.edtech.api.story.dto.StorySegmentResponse;
import com.java.edtech.domain.entity.StorySegment;
import com.java.edtech.repository.StorySegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StorySegmentService {
    private final StorySegmentRepository storySegmentRepository;

    @Transactional(readOnly = true)
    public List<StorySegmentResponse> getSegments(UUID storyId) {
        return storySegmentRepository.findByStoryIdOrderBySegmentOrderAsc(storyId).stream()
                .map(this::toResponse)
                .toList();
    }

    private StorySegmentResponse toResponse(StorySegment segment) {
        return StorySegmentResponse.builder()
                .id(segment.getId())
                .segmentOrder(segment.getSegmentOrder())
                .content(segment.getContent())
                .emotion(segment.getEmotion())
                .build();
    }
}
