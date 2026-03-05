package com.java.edtech.service.robot;

import com.java.edtech.websocket.dto.AudioFormat;

public interface AudioPipelineService {
    AudioPipelineResult processAudio(String sessionId,
                                     String utteranceId,
                                     byte[] compressedAudioBytes,
                                     Integer sampleRate,
                                     Integer channels,
                                     AudioFormat format);
}
