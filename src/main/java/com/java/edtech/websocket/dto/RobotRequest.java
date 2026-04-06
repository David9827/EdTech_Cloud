package com.java.edtech.websocket.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RobotRequest {
    public static final int DEFAULT_SAMPLE_RATE = 16000;
    public static final int DEFAULT_CHANNELS = 1;

    private RobotEventType type;
    private String sessionId;
    private String robotId;
    private String utteranceId;
    private String targetUtteranceId;
    private AudioFormat format = AudioFormat.OGG_OPUS;
    private Integer sampleRate = DEFAULT_SAMPLE_RATE;
    private Integer channels = DEFAULT_CHANNELS;
    private Integer frameDurationMs = 20;

}
