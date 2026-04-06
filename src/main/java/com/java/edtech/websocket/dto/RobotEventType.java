package com.java.edtech.websocket.dto;

public enum RobotEventType {
    HELLO,
    AUDIO_START,
    AUDIO_END,
    CANCEL_OUTPUT,
    ACK,
    OUTPUT_CANCELLED,
    TRANSCRIPT,
    ASSISTANT_REPLY,
    TTS_START,
    TTS_END,
    ERROR
}
