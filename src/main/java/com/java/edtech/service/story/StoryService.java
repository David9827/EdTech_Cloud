package com.java.edtech.service.story;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.java.edtech.api.story.dto.CreateStoryRequest;
import com.java.edtech.api.story.dto.CreateStoryResponse;
import com.java.edtech.api.story.dto.StoryPlaybackAudioChunk;
import com.java.edtech.api.story.dto.StoryPlaybackResponse;
import com.java.edtech.api.story.dto.StorySegmentResponse;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.domain.entity.Robot;
import com.java.edtech.domain.entity.Story;
import com.java.edtech.domain.entity.StoryPlaybackStateEntity;
import com.java.edtech.domain.entity.StorySegment;
import com.java.edtech.domain.enums.EmotionType;
import com.java.edtech.domain.enums.RobotStatus;
import com.java.edtech.domain.enums.StoryStatus;
import com.java.edtech.repository.RobotRepository;
import com.java.edtech.repository.StoryPlaybackStateRepository;
import com.java.edtech.repository.StoryRepository;
import com.java.edtech.repository.StorySegmentRepository;
import com.java.edtech.service.robot.TtsAudioResult;
import com.java.edtech.service.robot.TtsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoryService {
    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("(?<=[.!?…])\\s+");

    private final StoryRepository storyRepository;
    private final StorySegmentRepository storySegmentRepository;
    private final RobotRepository robotRepository;
    private final StoryPlaybackStateRepository storyPlaybackStateRepository;
    private final TtsService ttsService;
    private final ConcurrentHashMap<UUID, StoryPlaybackState> playbackStates = new ConcurrentHashMap<>();

    @Transactional
    public CreateStoryResponse createStory(CreateStoryRequest request) {
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setTitle(request.getTitle().trim());
        story.setStatus(request.getStatus() == null ? StoryStatus.PUBLISHED : request.getStatus());
        story.setTopicId(request.getTopicId());
        story.setMinAge(request.getMinAge());
        story.setMaxAge(request.getMaxAge());
        story.setCreatedAt(Instant.now());
        Story savedStory = storyRepository.save(story);

        int maxChars = normalizeMaxSegmentChars(request.getMaxSegmentChars());
        List<String> parts = splitStoryContent(request.getContent(), maxChars);
        if (parts.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "STORY_CONTENT_EMPTY", "Story content is empty after normalization");
        }

        List<StorySegmentResponse> segmentResponses = new ArrayList<>();
        int order = 1;
        for (String part : parts) {
            StorySegment segment = new StorySegment();
            segment.setId(UUID.randomUUID());
            segment.setStory(savedStory);
            segment.setSegmentOrder(order++);
            segment.setContent(part);
            segment.setEmotion(EmotionType.NEUTRAL);
            StorySegment savedSegment = storySegmentRepository.save(segment);
            segmentResponses.add(
                    StorySegmentResponse.builder()
                            .id(savedSegment.getId())
                            .segmentOrder(savedSegment.getSegmentOrder())
                            .content(savedSegment.getContent())
                            .emotion(savedSegment.getEmotion())
                            .build()
            );
        }

        return CreateStoryResponse.builder()
                .storyId(savedStory.getId())
                .title(savedStory.getTitle())
                .status(savedStory.getStatus())
                .totalSegments(segmentResponses.size())
                .segments(segmentResponses)
                .build();
    }

    @Transactional
    public StoryPlaybackAudioChunk startPlayback(UUID robotId, UUID storyId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ROBOT_NOT_FOUND", "Robot not found"));
        Story story = storyRepository.findByIdAndStatus(storyId, StoryStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "STORY_NOT_FOUND", "Published story not found"));

        StorySegment first = storySegmentRepository.findFirstByStoryIdOrderBySegmentOrderAsc(storyId)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "STORY_EMPTY", "Story has no segments"));

        robot.setStatus(RobotStatus.STORY_PLAYING);
        robotRepository.save(robot);

        StoryPlaybackState state = new StoryPlaybackState(storyId, first.getSegmentOrder());
        playbackStates.put(robotId, state);
        savePlaybackState(robotId, story, first.getSegmentOrder());

        return buildPlaybackResponse(robotId, story, first, false);
    }

    @Transactional
    public StoryPlaybackAudioChunk nextPlayback(UUID robotId) {
        StoryPlaybackState state = resolvePlaybackState(robotId);
        if (state == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "PLAYBACK_NOT_STARTED", "Story playback is not started");
        }

        Story story = storyRepository.findById(state.storyId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "STORY_NOT_FOUND", "Story not found"));

        StorySegment next = storySegmentRepository
                .findFirstByStoryIdAndSegmentOrderGreaterThanOrderBySegmentOrderAsc(state.storyId(), state.currentSegmentOrder())
                .orElse(null);

        if (next == null) {
            playbackStates.remove(robotId);
            storyPlaybackStateRepository.deleteById(robotId);
            setRobotIdle(robotId);
            return StoryPlaybackAudioChunk.builder()
                    .robotId(robotId)
                    .storyId(story.getId())
                    .storyTitle(story.getTitle())
                    .completed(true)
                    .audioBytes(new byte[0])
                    .audioMimeType("audio/wav")
                    .build();
        }

        StoryPlaybackState nextState = new StoryPlaybackState(state.storyId(), next.getSegmentOrder());
        playbackStates.put(robotId, nextState);
        savePlaybackState(robotId, story, next.getSegmentOrder());
        return buildPlaybackResponse(robotId, story, next, false);
    }

    @Transactional
    public StoryPlaybackResponse stopPlayback(UUID robotId) {
        StoryPlaybackState removed = playbackStates.remove(robotId);
        storyPlaybackStateRepository.deleteById(robotId);
        setRobotIdle(robotId);
        if (removed == null) {
            return StoryPlaybackResponse.builder()
                    .robotId(robotId)
                    .completed(true)
                    .build();
        }

        Story story = storyRepository.findById(removed.storyId()).orElse(null);
        return StoryPlaybackResponse.builder()
                .robotId(robotId)
                .storyId(removed.storyId())
                .storyTitle(story == null ? null : story.getTitle())
                .completed(true)
                .build();
    }

    private StoryPlaybackAudioChunk buildPlaybackResponse(UUID robotId, Story story, StorySegment segment, boolean completed) {
        TtsAudioResult tts = ttsService.synthesize(segment.getContent());

        return StoryPlaybackAudioChunk.builder()
                .robotId(robotId)
                .storyId(story.getId())
                .storyTitle(story.getTitle())
                .segmentId(segment.getId())
                .segmentOrder(segment.getSegmentOrder())
                .content(segment.getContent())
                .emotion(segment.getEmotion())
                .completed(completed)
                .audioMimeType(tts.getMimeType())
                .sampleRate(tts.getSampleRate())
                .channels(tts.getChannels())
                .audioBytes(tts.getAudioBytes() == null ? new byte[0] : tts.getAudioBytes())
                .build();
    }

    private void setRobotIdle(UUID robotId) {
        robotRepository.findById(robotId).ifPresent(robot -> {
            robot.setStatus(RobotStatus.IDLE);
            robotRepository.save(robot);
        });
    }

    private StoryPlaybackState resolvePlaybackState(UUID robotId) {
        StoryPlaybackState inMemory = playbackStates.get(robotId);
        if (inMemory != null) {
            return inMemory;
        }
        StoryPlaybackStateEntity persisted = storyPlaybackStateRepository.findById(robotId).orElse(null);
        if (persisted == null || persisted.getStory() == null) {
            return null;
        }
        StoryPlaybackState recovered = new StoryPlaybackState(
                persisted.getStory().getId(),
                persisted.getCurrentSegmentOrder()
        );
        playbackStates.put(robotId, recovered);
        return recovered;
    }

    private void savePlaybackState(UUID robotId, Story story, Integer segmentOrder) {
        StoryPlaybackStateEntity state = new StoryPlaybackStateEntity();
        state.setRobotId(robotId);
        state.setStory(story);
        state.setCurrentSegmentOrder(segmentOrder);
        storyPlaybackStateRepository.save(state);
    }

    private int normalizeMaxSegmentChars(Integer value) {
        if (value == null) {
            return 240;
        }
        return Math.max(80, Math.min(500, value));
    }

    private List<String> splitStoryContent(String content, int maxChars) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String normalized = content.trim().replaceAll("\\s+", " ");
        String[] sentences = SENTENCE_SPLITTER.split(normalized);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentenceRaw : sentences) {
            String sentence = sentenceRaw.trim();
            if (sentence.isEmpty()) {
                continue;
            }

            if (sentence.length() > maxChars) {
                flushCurrent(chunks, current);
                splitLongSentence(sentence, maxChars, chunks);
                continue;
            }

            if (current.length() == 0) {
                current.append(sentence);
                continue;
            }

            if (current.length() + 1 + sentence.length() <= maxChars) {
                current.append(" ").append(sentence);
            } else {
                chunks.add(current.toString().trim());
                current.setLength(0);
                current.append(sentence);
            }
        }
        flushCurrent(chunks, current);
        return chunks;
    }

    private void splitLongSentence(String sentence, int maxChars, List<String> chunks) {
        String[] words = sentence.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
                continue;
            }
            if (current.length() + 1 + word.length() <= maxChars) {
                current.append(" ").append(word);
            } else {
                chunks.add(current.toString().trim());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
    }

    private void flushCurrent(List<String> chunks, StringBuilder current) {
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
            current.setLength(0);
        }
    }

    private record StoryPlaybackState(UUID storyId, Integer currentSegmentOrder) {
    }
}
