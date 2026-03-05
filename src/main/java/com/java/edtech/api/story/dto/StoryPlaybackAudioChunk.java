package com.java.edtech.api.story.dto;

import java.util.UUID;

import com.java.edtech.domain.enums.EmotionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoryPlaybackAudioChunk {
    private UUID robotId;
    private UUID storyId;
    private String storyTitle;
    private UUID segmentId;
    private Integer segmentOrder;
    private String content;
    private EmotionType emotion;
    private boolean completed;
    private String audioMimeType;
    private Integer sampleRate;
    private Integer channels;
    private byte[] audioBytes;
}
