package com.java.edtech.service.robot;

import com.java.edtech.websocket.dto.AudioFormat;

public interface SttService {
    String transcribe(byte[] compressedAudioBytes, Integer sampleRate, Integer channels, AudioFormat format);
}
