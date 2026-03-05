package com.java.edtech.api.story.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StopStoryPlaybackRequest {
    @NotNull(message = "robotId is required")
    private UUID robotId;
}
