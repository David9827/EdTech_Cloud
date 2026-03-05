package com.java.edtech.api.story.dto;

import java.util.UUID;

import com.java.edtech.domain.enums.StoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateStoryRequest {
    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "content is required")
    private String content;

    private UUID topicId;
    private Integer minAge;
    private Integer maxAge;

    @NotNull(message = "status is required")
    private StoryStatus status = StoryStatus.PUBLISHED;

    private Integer maxSegmentChars = 240;
}
