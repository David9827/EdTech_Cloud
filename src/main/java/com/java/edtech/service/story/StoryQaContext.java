package com.java.edtech.service.story;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoryQaContext {
    private UUID storyId;
    private String storyTitle;
    private Integer currentSegmentOrder;
    private String recentContext;
}
