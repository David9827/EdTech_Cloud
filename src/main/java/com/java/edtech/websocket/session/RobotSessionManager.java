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
        synchronized (state) {
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
    }

    public boolean appendAudio(WebSocketSession session, byte[] payload) {
        SessionState state = sessions.get(session.getId());
        if (state == null) {
            return false;
        }
        synchronized (state) {
            if (state.audioBuffer == null) {
                return false;
            }
            state.audioBuffer.writeBytes(payload);
            state.frameCount++;
            return true;
        }
    }

    public SessionState endUtterance(WebSocketSession session) {
        SessionState state = sessions.get(session.getId());
        if (state == null) {
            return null;
        }
        synchronized (state) {
            SessionState snapshot = state.copy();
            state.audioBuffer = null;
            state.utteranceId = null;
            return snapshot;
        }
    }

    public String requestCancelOutput(WebSocketSession session, String targetUtteranceId) {
        SessionState state = sessions.computeIfAbsent(session.getId(), k -> new SessionState());
        synchronized (state) {
            String target = normalize(targetUtteranceId);
            if (target == null) {
                target = normalize(state.activeOutputUtteranceId);
            }
            if (target == null) {
                return null;
            }
            state.cancelTargetUtteranceId = target;
            return target;
        }
    }

    public void markOutputStarted(WebSocketSession session, String utteranceId) {
        SessionState state = sessions.computeIfAbsent(session.getId(), k -> new SessionState());
        synchronized (state) {
            state.activeOutputUtteranceId = utteranceId;
        }
    }

    public boolean shouldCancelOutput(WebSocketSession session, String utteranceId) {
        SessionState state = sessions.get(session.getId());
        if (state == null) {
            return false;
        }
        synchronized (state) {
            String target = normalize(state.cancelTargetUtteranceId);
            String current = normalize(utteranceId);
            return target != null && current != null && target.equals(current);
        }
    }

    public void finishOutput(WebSocketSession session, String utteranceId) {
        SessionState state = sessions.get(session.getId());
        if (state == null) {
            return;
        }
        synchronized (state) {
            String current = normalize(utteranceId);
            if (current == null) {
                return;
            }
            if (current.equals(normalize(state.activeOutputUtteranceId))) {
                state.activeOutputUtteranceId = null;
            }
            if (current.equals(normalize(state.cancelTargetUtteranceId))) {
                state.cancelTargetUtteranceId = null;
            }
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        private String activeOutputUtteranceId;
        private String cancelTargetUtteranceId;

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
