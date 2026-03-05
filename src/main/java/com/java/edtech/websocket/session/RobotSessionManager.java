package com.java.edtech.websocket.session;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.java.edtech.websocket.dto.AudioFormat;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RobotSessionManager {
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), new SessionState());
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
    }

    public void startUtterance(WebSocketSession session, String sessionId, String robotId, String utteranceId,
                               AudioFormat format, Integer sampleRate, Integer channels, Integer frameDurationMs) {
        SessionState state = sessions.computeIfAbsent(session.getId(), k -> new SessionState());
        state.sessionId = sessionId;
        state.robotId = robotId;
        state.utteranceId = utteranceId;
        state.format = format;
        state.sampleRate = sampleRate;
        state.channels = channels;
        state.frameDurationMs = frameDurationMs;
        state.frameCount = 0;
        state.audioBuffer = new ByteArrayOutputStream();
    }

    public boolean appendAudio(WebSocketSession session, byte[] payload) {
        SessionState state = sessions.get(session.getId());
        if (state == null || state.audioBuffer == null) {
            return false;
        }
        state.audioBuffer.writeBytes(payload);
        state.frameCount++;
        return true;
    }

    public SessionState endUtterance(WebSocketSession session) {
        SessionState state = sessions.get(session.getId());
        if (state == null) {
            return null;
        }
        SessionState snapshot = state.copy();
        state.audioBuffer = null;
        state.utteranceId = null;
        return snapshot;
    }

    public static class SessionState {
        private String sessionId;
        private String robotId;
        private String utteranceId;
        private AudioFormat format;
        private Integer sampleRate;
        private Integer channels;
        private Integer frameDurationMs;
        private Integer frameCount;
        private ByteArrayOutputStream audioBuffer;

        public String getSessionId() {
            return sessionId;
        }

        public String getRobotId() {
            return robotId;
        }

        public String getUtteranceId() {
            return utteranceId;
        }

        public AudioFormat getFormat() {
            return format;
        }

        public Integer getSampleRate() {
            return sampleRate;
        }

        public Integer getChannels() {
            return channels;
        }

        public Integer getFrameDurationMs() {
            return frameDurationMs;
        }

        public Integer getFrameCount() {
            return frameCount;
        }

        public byte[] getAudioBytes() {
            return audioBuffer == null ? new byte[0] : audioBuffer.toByteArray();
        }

        private SessionState copy() {
            SessionState copy = new SessionState();
            copy.sessionId = this.sessionId;
            copy.robotId = this.robotId;
            copy.utteranceId = this.utteranceId;
            copy.format = this.format;
            copy.sampleRate = this.sampleRate;
            copy.channels = this.channels;
            copy.frameDurationMs = this.frameDurationMs;
            copy.frameCount = this.frameCount;
            copy.audioBuffer = this.audioBuffer;
            return copy;
        }
    }
}
