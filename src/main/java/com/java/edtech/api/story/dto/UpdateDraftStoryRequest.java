package com.java.edtech.api.story.dto;

import java.util.UUID;

import com.java.edtech.domain.enums.StoryStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDraftStoryRequest {
    private String title;
    private String content;
    private UUID topicId;
    private Integer minAge;
    private Integer maxAge;
    private String imgUrl;
    private Integer maxSegmentChars;
    private StoryStatus status;
}
