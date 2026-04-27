package com.java.edtech.websocket.handler;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.edtech.service.conversation.MessageService;
import com.java.edtech.service.robot.AudioPipelineResult;
import com.java.edtech.service.robot.AudioPipelineService;
import com.java.edtech.service.robot.TtsAudioResult;
import com.java.edtech.service.robot.TtsService;
import com.java.edtech.websocket.dto.AudioFormat;
import com.java.edtech.websocket.dto.RobotEventType;
import com.java.edtech.websocket.dto.RobotRequest;
import com.java.edtech.websocket.dto.RobotResponse;
import com.java.edtech.websocket.session.RobotSessionManager;
import com.java.edtech.websocket.session.RobotSessionManager.SessionState;

@Component
public class RobotWebSocketHandler extends AbstractWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(RobotWebSocketHandler.class);
    private static final int TTS_WS_CHUNK_BYTES = 4096;

    private final ObjectMapper objectMapper;
    private final RobotSessionManager sessionManager;
    private final AudioPipelineService audioPipelineService;
    private final TtsService ttsService;
    private final MessageService messageService;
    private final ExecutorService pipelineExecutor;

    public RobotWebSocketHandler(ObjectMapper objectMapper,
                                 RobotSessionManager sessionManager,
                                 AudioPipelineService audioPipelineService,
                                 TtsService ttsService,
                                 MessageService messageService) {
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.audioPipelineService = audioPipelineService;
        this.ttsService = ttsService;
        this.messageService = messageService;
        this.pipelineExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("ws-audio-pipeline"));
    }

    @PreDestroy
    public void shutdownExecutor() {
        pipelineExecutor.shutdownNow();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.unregister(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        RobotRequest request = objectMapper.readValue(message.getPayload(), RobotRequest.class);
        if (request.getType() == null) {
            sendError(session, request, "INVALID_MESSAGE", "Missing type");
            return;
        }

        switch (request.getType()) {
            case HELLO -> sendAck(session, request);
            case AUDIO_START -> handleAudioStart(session, request);
            case AUDIO_END -> handleAudioEndAsync(session, request);
            case CANCEL_OUTPUT -> handleCancelOutput(session, request);
            default -> sendError(session, request, "UNSUPPORTED_TYPE", "Unsupported type: " + request.getType());
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        int size = message.getPayloadLength();
        byte[] payload = new byte[size];
        message.getPayload().get(payload);
        if (!sessionManager.appendAudio(session, payload)) {
            sendError(session, null, "AUDIO_NOT_STARTED", "Send AUDIO_START before binary frames");
        }
    }

    private void handleAudioStart(WebSocketSession session, RobotRequest request) throws IOException {
        if (!isValidAudioStart(request)) {
            sendError(
                    session,
                    request,
                    "INVALID_AUDIO_CONFIG",
                    "Use format=OGG_OPUS or PCM_16BIT, sampleRate=16000, channels=1"
            );
            return;
        }
        String sessionId = request.getSessionId() == null ? session.getId() : request.getSessionId();
        sessionManager.startUtterance(
                session,
                sessionId,
                request.getRobotId(),
                request.getUtteranceId(),
                request.getFormat(),
                request.getSampleRate(),
                request.getChannels(),
                request.getFrameDurationMs()
        );
        sendAck(session, request);
    }

    private void handleAudioEndAsync(WebSocketSession session, RobotRequest request) throws IOException {
        SessionState state = sessionManager.endUtterance(session);
        if (state == null || state.getAudioBytes().length == 0) {
            sendError(session, request, "NO_AUDIO", "No audio received");
            return;
        }
        log.info("[LATENCY][AUDIO_END] session={} utterance={} audioEndReceivedEpochMs={} audioBytes={}",
                state.getSessionId(),
                state.getUtteranceId(),
                System.currentTimeMillis(),
                state.getAudioBytes().length);

        CompletableFuture.runAsync(() -> processAudioEnd(session, request, state), pipelineExecutor)
                .exceptionally(ex -> {
                    log.error("WS audio pipeline crashed session={} utterance={}",
                            session.getId(), state.getUtteranceId(), ex);
                    safeSendError(session, request, "PIPELINE_ERROR", "Audio pipeline crashed");
                    return null;
                });
    }

    private void processAudioEnd(WebSocketSession session, RobotRequest request, SessionState state) {
        try {
            AudioPipelineResult result = audioPipelineService.processAudio(
                    state.getSessionId(),
                    state.getRobotId(),
                    state.getUtteranceId(),
                    state.getAudioBytes(),
                    state.getSampleRate(),
                    state.getChannels(),
                    state.getFormat()
            );
            if (result.getError() != null && !result.getError().isBlank()) {
                safeSendError(session, request, "PIPELINE_ERROR", result.getError());
                return;
            }

            RobotResponse transcriptResponse = new RobotResponse();
            transcriptResponse.setType(RobotEventType.TRANSCRIPT);
            transcriptResponse.setSessionId(state.getSessionId());
            transcriptResponse.setUtteranceId(state.getUtteranceId());
            transcriptResponse.setText(result.getTranscript());
            safeSendJson(session, transcriptResponse);

            RobotResponse replyResponse = new RobotResponse();
            replyResponse.setType(RobotEventType.ASSISTANT_REPLY);
            replyResponse.setSessionId(state.getSessionId());
            replyResponse.setUtteranceId(state.getUtteranceId());
            replyResponse.setText(result.getAssistantReply());
            safeSendJson(session, replyResponse);

            messageService.saveTurnFromWs(
                    state.getSessionId(),
                    state.getRobotId(),
                    result.getTranscript(),
                    result.getAssistantReply()
            );

            TtsAudioResult ttsAudio = ttsService.synthesize(result.getAssistantReply());
            if (ttsAudio.getAudioBytes() == null || ttsAudio.getAudioBytes().length == 0) {
                return;
            }
            long ttsReadyEpochMs = System.currentTimeMillis();

            String utteranceId = state.getUtteranceId();
            sessionManager.markOutputStarted(session, utteranceId);
            try {
                if (sessionManager.shouldCancelOutput(session, utteranceId)) {
                    log.info("Skip TTS, output cancelled before stream start. session={} utterance={}",
                            session.getId(), utteranceId);
                    sendTtsEnd(session, state);
                    return;
                }

                RobotResponse ttsStart = new RobotResponse();
                ttsStart.setType(RobotEventType.TTS_START);
                ttsStart.setSessionId(state.getSessionId());
                ttsStart.setUtteranceId(utteranceId);
                ttsStart.setAudioMimeType(ttsAudio.getMimeType());
                ttsStart.setSampleRate(ttsAudio.getSampleRate());
                ttsStart.setChannels(ttsAudio.getChannels());
                ttsStart.setAudioBytesLength(ttsAudio.getAudioBytes().length);
                safeSendJson(session, ttsStart);

                boolean cancelled = sendBinaryInChunks(
                        session,
                        ttsAudio.getAudioBytes(),
                        state.getSessionId(),
                        utteranceId,
                        ttsReadyEpochMs
                );
                if (cancelled) {
                    log.info("TTS stream cancelled mid-flight. session={} utterance={}", session.getId(), utteranceId);
                }

                sendTtsEnd(session, state);
            } finally {
                sessionManager.finishOutput(session, utteranceId);
            }
        } catch (Exception ex) {
            log.error("WS audio pipeline error session={} utterance={}", session.getId(), state.getUtteranceId(), ex);
            safeSendError(session, request, "PIPELINE_ERROR", "Failed to process audio");
        }
    }

    private void handleCancelOutput(WebSocketSession session, RobotRequest request) throws IOException {
        String cancelledUtteranceId = sessionManager.requestCancelOutput(session, request.getTargetUtteranceId());
        RobotResponse response = new RobotResponse();
        response.setType(RobotEventType.OUTPUT_CANCELLED);
        response.setSessionId(request.getSessionId() == null ? session.getId() : request.getSessionId());
        response.setUtteranceId(request.getUtteranceId());
        response.setCancelledUtteranceId(cancelledUtteranceId);
        sendJson(session, response);
    }

    private boolean isValidAudioStart(RobotRequest request) {
        if (request.getUtteranceId() == null || request.getUtteranceId().isBlank()) {
            return false;
        }
        if (request.getFormat() != AudioFormat.OGG_OPUS && request.getFormat() != AudioFormat.PCM_16BIT) {
            return false;
        }
        if (request.getSampleRate() == null || request.getSampleRate() != RobotRequest.DEFAULT_SAMPLE_RATE) {
            return false;
        }
        return request.getChannels() != null && request.getChannels() == RobotRequest.DEFAULT_CHANNELS;
    }

    private void sendAck(WebSocketSession session, RobotRequest request) throws IOException {
        RobotResponse response = new RobotResponse();
        response.setType(RobotEventType.ACK);
        response.setSessionId(request.getSessionId() == null ? session.getId() : request.getSessionId());
        response.setUtteranceId(request.getUtteranceId());
        sendJson(session, response);
    }

    private void sendTtsEnd(WebSocketSession session, SessionState state) {
        RobotResponse ttsEnd = new RobotResponse();
        ttsEnd.setType(RobotEventType.TTS_END);
        ttsEnd.setSessionId(state.getSessionId());
        ttsEnd.setUtteranceId(state.getUtteranceId());
        safeSendJson(session, ttsEnd);
    }

    private void sendError(WebSocketSession session, RobotRequest request, String code, String message)
            throws IOException {
        RobotResponse response = new RobotResponse();
        response.setType(RobotEventType.ERROR);
        response.setSessionId(request == null ? session.getId() : request.getSessionId());
        response.setUtteranceId(request == null ? null : request.getUtteranceId());
        response.setErrorCode(code);
        response.setErrorMessage(message);
        sendJson(session, response);
    }

    private void safeSendError(WebSocketSession session, RobotRequest request, String code, String message) {
        try {
            sendError(session, request, code, message);
        } catch (IOException e) {
            log.debug("Failed to send WS error frame session={} code={}", session.getId(), code, e);
        }
    }

    private void sendJson(WebSocketSession session, RobotResponse response) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        String payload = objectMapper.writeValueAsString(response);
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        }
    }

    private void safeSendJson(WebSocketSession session, RobotResponse response) {
        try {
            sendJson(session, response);
        } catch (IOException e) {
            log.debug("Failed to send WS json frame session={} type={}",
                    session.getId(),
                    response.getType(),
                    e);
        }
    }

    // Returns true when stream was cancelled, false when stream fully sent.
    private boolean sendBinaryInChunks(WebSocketSession session,
                                       byte[] audioBytes,
                                       String sessionId,
                                       String utteranceId,
                                       long ttsReadyEpochMs) {
        if (audioBytes == null || audioBytes.length == 0) {
            return false;
        }

        int offset = 0;
        while (offset < audioBytes.length) {
            if (sessionManager.shouldCancelOutput(session, utteranceId)) {
                return true;
            }
            if (!session.isOpen()) {
                return true;
            }

            int len = Math.min(TTS_WS_CHUNK_BYTES, audioBytes.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(audioBytes, offset, chunk, 0, len);

            try {
                synchronized (session) {
                    if (!session.isOpen()) {
                        return true;
                    }
                    session.sendMessage(new BinaryMessage(chunk));
                    if (offset == 0) {
                        long firstBinaryEpochMs = System.currentTimeMillis();
                        log.info("[LATENCY][T4] session={} utterance={} ttsReadyEpochMs={} firstBinaryEpochMs={} ttsToFirstBinaryMs={} firstChunkBytes={} totalAudioBytes={}",
                                sessionId,
                                utteranceId,
                                ttsReadyEpochMs,
                                firstBinaryEpochMs,
                                firstBinaryEpochMs - ttsReadyEpochMs,
                                len,
                                audioBytes.length);
                    }
                }
            } catch (IOException e) {
                log.debug("Failed to send WS binary chunk session={} utterance={}", session.getId(), utteranceId, e);
                return true;
            }
            offset += len;
        }
        return false;
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger seq = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
