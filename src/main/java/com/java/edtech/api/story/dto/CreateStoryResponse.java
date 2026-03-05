package com.java.edtech.api.story.dto;

import java.util.List;
import java.util.UUID;

import com.java.edtech.domain.enums.StoryStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateStoryResponse {
    private UUID storyId;
    private String title;
    private StoryStatus status;
    private int totalSegments;
    private List<StorySegmentResponse> segments;
}
