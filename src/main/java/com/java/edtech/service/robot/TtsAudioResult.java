package com.java.edtech.service.robot;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TtsAudioResult {
    private byte[] audioBytes;
    private String mimeType;
    private Integer sampleRate;
    private Integer channels;
}
