package com.java.edtech.api.story.dto;

import java.time.Instant;
import java.util.UUID;

import com.java.edtech.domain.enums.StoryStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StorySummaryResponse {
    private UUID id;
    private String title;
    private UUID topicId;
    private Integer minAge;
    private Integer maxAge;
    private String imgUrl;
    private StoryStatus status;
    private Instant createdAt;
}
