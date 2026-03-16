package com.java.edtech.api.story.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoryListResponse {
    private int total;
    private List<StorySummaryResponse> items;
}
