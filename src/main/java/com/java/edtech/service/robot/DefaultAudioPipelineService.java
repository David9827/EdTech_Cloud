package com.java.edtech.service.robot;

import java.util.UUID;

import com.java.edtech.llm.dto.LlmResponse;
import com.java.edtech.llm.service.LlmService;
import com.java.edtech.service.story.StoryQaContext;
import com.java.edtech.service.story.StoryService;
import com.java.edtech.websocket.dto.AudioFormat;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultAudioPipelineService implements AudioPipelineService {
    private static final int STORY_QA_CONTEXT_SEGMENTS = 4;
    private static final Logger log = LoggerFactory.getLogger(DefaultAudioPipelineService.class);

    private final SttService sttService;
    private final LlmService llmService;
    private final StoryService storyService;

    @Override
    public AudioPipelineResult processAudio(String sessionId,
                                            String robotId,
                                            String utteranceId,
                                            byte[] compressedAudioBytes,
                                            Integer sampleRate,
                                            Integer channels,
                                            AudioFormat format) {
        String transcript = sttService.transcribe(compressedAudioBytes, sampleRate, channels, format);
        log.info("[LATENCY][T2] session={} utterance={} sttDoneEpochMs={} audioBytes={} transcriptChars={}",
                sessionId,
                utteranceId,
                System.currentTimeMillis(),
                compressedAudioBytes == null ? 0 : compressedAudioBytes.length,
                transcript == null ? 0 : transcript.length());
        if (transcript == null || transcript.isBlank()) {
            return AudioPipelineResult.builder()
                    .transcript("")
                    .assistantReply("")
                    .error("Empty transcript from STT")
                    .build();
        }

        StoryQaContext storyQaContext = resolveStoryQaContext(robotId);
        LlmResponse llmResponse = storyQaContext == null
                ? llmService.generateReply(transcript)
                : llmService.generateStoryQaReply(buildStoryQaInput(storyQaContext, transcript));
        log.info("[LATENCY][T3] session={} utterance={} llmDoneEpochMs={} success={} replyChars={} error={}",
                sessionId,
                utteranceId,
                System.currentTimeMillis(),
                llmResponse.isSuccess(),
                llmResponse.getText() == null ? 0 : llmResponse.getText().length(),
                llmResponse.getError() == null ? "" : llmResponse.getError());

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

    private StoryQaContext resolveStoryQaContext(String robotId) {
        if (robotId == null || robotId.isBlank()) {
            return null;
        }
        try {
            UUID robotUuid = UUID.fromString(robotId);
            return storyService.getQaContext(robotUuid, STORY_QA_CONTEXT_SEGMENTS);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String buildStoryQaInput(StoryQaContext context, String question) {
        return "Tiêu đề truyện: " + context.getStoryTitle()
                + "\nĐang ở đoạn: " + context.getCurrentSegmentOrder()
                + "\nCác đoạn đã kể gần đây:\n" + context.getRecentContext()
                + "\nCâu hỏi của bé: " + question;
    }
}
