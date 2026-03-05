package com.java.edtech.service.robot;

import com.java.edtech.llm.dto.LlmResponse;
import com.java.edtech.llm.service.LlmService;
import com.java.edtech.websocket.dto.AudioFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultAudioPipelineService implements AudioPipelineService {
    private final SttService sttService;
    private final LlmService llmService;

    @Override
    public AudioPipelineResult processAudio(String sessionId,
                                            String utteranceId,
                                            byte[] compressedAudioBytes,
                                            Integer sampleRate,
                                            Integer channels,
                                            AudioFormat format) {
        String transcript = sttService.transcribe(compressedAudioBytes, sampleRate, channels, format);
        if (transcript == null || transcript.isBlank()) {
            return AudioPipelineResult.builder()
                    .transcript("")
                    .assistantReply("")
                    .error("Empty transcript from STT")
                    .build();
        }

        LlmResponse llmResponse = llmService.generateReply(transcript);
        if (!llmResponse.isSuccess()) {
            return AudioPipelineResult.builder()
                    .transcript(transcript)
                    .assistantReply("")
                    .error(llmResponse.getError())
                    .build();
        }

        return AudioPipelineResult.builder()
                .transcript(transcript)
                .assistantReply(llmResponse.getText())
                .build();
    }
}
