package com.java.edtech.websocket.handler;

import java.io.IOException;

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
    private static final int TTS_WS_CHUNK_BYTES = 4096;

    private final ObjectMapper objectMapper;
    private final RobotSessionManager sessionManager;
    private final AudioPipelineService audioPipelineService;
    private final TtsService ttsService;
    private final MessageService messageService;

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
            case AUDIO_START -> {
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
            case AUDIO_END -> {
                SessionState state = sessionManager.endUtterance(session);
                if (state == null || state.getAudioBytes().length == 0) {
                    sendError(session, request, "NO_AUDIO", "No audio received");
                    return;
                }
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
                    sendError(session, request, "PIPELINE_ERROR", result.getError());
                    return;
                }

                RobotResponse transcriptResponse = new RobotResponse();
                transcriptResponse.setType(RobotEventType.TRANSCRIPT);
                transcriptResponse.setSessionId(state.getSessionId());
                transcriptResponse.setUtteranceId(state.getUtteranceId());
                transcriptResponse.setText(result.getTranscript());
                sendJson(session, transcriptResponse);

                RobotResponse replyResponse = new RobotResponse();
                replyResponse.setType(RobotEventType.ASSISTANT_REPLY);
                replyResponse.setSessionId(state.getSessionId());
                replyResponse.setUtteranceId(state.getUtteranceId());
                replyResponse.setText(result.getAssistantReply());
                sendJson(session, replyResponse);

                messageService.saveTurnFromWs(
                        state.getSessionId(),
                        state.getRobotId(),
                        result.getTranscript(),
                        result.getAssistantReply()
                );

                TtsAudioResult ttsAudio = ttsService.synthesize(result.getAssistantReply());
                if (ttsAudio.getAudioBytes() != null && ttsAudio.getAudioBytes().length > 0) {
                    RobotResponse ttsStart = new RobotResponse();
                    ttsStart.setType(RobotEventType.TTS_START);
                    ttsStart.setSessionId(state.getSessionId());
                    ttsStart.setUtteranceId(state.getUtteranceId());
                    ttsStart.setAudioMimeType(ttsAudio.getMimeType());
                    ttsStart.setSampleRate(ttsAudio.getSampleRate());
                    ttsStart.setChannels(ttsAudio.getChannels());
                    ttsStart.setAudioBytesLength(ttsAudio.getAudioBytes().length);
                    sendJson(session, ttsStart);

                    sendBinaryInChunks(session, ttsAudio.getAudioBytes());

                    RobotResponse ttsEnd = new RobotResponse();
                    ttsEnd.setType(RobotEventType.TTS_END);
                    ttsEnd.setSessionId(state.getSessionId());
                    ttsEnd.setUtteranceId(state.getUtteranceId());
                    sendJson(session, ttsEnd);
                }
            }
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

    private void sendJson(WebSocketSession session, RobotResponse response) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private void sendBinaryInChunks(WebSocketSession session, byte[] audioBytes) throws IOException {
        if (audioBytes == null || audioBytes.length == 0) {
            return;
        }

        int offset = 0;
        while (offset < audioBytes.length) {
            int len = Math.min(TTS_WS_CHUNK_BYTES, audioBytes.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(audioBytes, offset, chunk, 0, len);
            session.sendMessage(new BinaryMessage(chunk));
            offset += len;
        }
    }
}
