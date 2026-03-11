package com.java.edtech.service.story;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoryService {
    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("(?<=[.!?\\u2026])\\s+");
    private static final String REDIS_PLAYBACK_STATE_KEY_PREFIX = "story:playback:robot:";
    private static final String REDIS_SEGMENTS_KEY_PREFIX = "story:segments:";
    private static final String REDIS_AUDIO_KEY_PREFIX = "story:audio:segment:";
    private static final Duration PLAYBACK_STATE_TTL = Duration.ofMinutes(30);
    private static final Duration SEGMENTS_TTL = Duration.ofHours(6);
    private static final Duration SEGMENT_AUDIO_TTL = Duration.ofHours(2);

    private final StoryRepository storyRepository;
    private final StorySegmentRepository storySegmentRepository;
    private final RobotRepository robotRepository;
    private final StoryPlaybackStateRepository storyPlaybackStateRepository;
    private final TtsService ttsService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<UUID, StoryPlaybackState> playbackStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<StorySegmentSnapshot>> storySegmentsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TtsAudioResult> segmentAudioCache = new ConcurrentHashMap<>();
    private final ExecutorService playbackPrefetchExecutor = Executors.newFixedThreadPool(2);

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

        storySegmentsCache.remove(savedStory.getId());
        stringRedisTemplate.delete(storySegmentsKey(savedStory.getId()));

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

        List<StorySegmentSnapshot> segments = getStorySegments(storyId);
        StorySegmentSnapshot first = segments.get(0);

        robot.setStatus(RobotStatus.STORY_PLAYING);
        robotRepository.save(robot);

        StoryPlaybackState state = new StoryPlaybackState(storyId, first.segmentOrder());
        playbackStates.put(robotId, state);
        savePlaybackState(robotId, story, first.segmentOrder());

        StoryPlaybackAudioChunk chunk = buildPlaybackResponse(robotId, story, first, false);
        prefetchNextAudio(first.segmentOrder(), segments);
        return chunk;
    }

    @Transactional
    public StoryPlaybackAudioChunk nextPlayback(UUID robotId) {
        StoryPlaybackState state = resolvePlaybackState(robotId);
        if (state == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "PLAYBACK_NOT_STARTED", "Story playback is not started");
        }

        Story story = storyRepository.findById(state.storyId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "STORY_NOT_FOUND", "Story not found"));

        List<StorySegmentSnapshot> segments = getStorySegments(state.storyId());
        StorySegmentSnapshot next = findNextSegment(segments, state.currentSegmentOrder());

        if (next == null) {
            clearPlaybackState(robotId);
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

        StoryPlaybackState nextState = new StoryPlaybackState(state.storyId(), next.segmentOrder());
        playbackStates.put(robotId, nextState);
        savePlaybackState(robotId, story, next.segmentOrder());

        StoryPlaybackAudioChunk chunk = buildPlaybackResponse(robotId, story, next, false);
        prefetchNextAudio(next.segmentOrder(), segments);
        return chunk;
    }

    @Transactional
    public StoryPlaybackResponse stopPlayback(UUID robotId) {
        StoryPlaybackState removed = resolvePlaybackState(robotId);
        clearPlaybackState(robotId);
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

    @Transactional(readOnly = true)
    public StoryQaContext getQaContext(UUID robotId, int recentSegmentCount) {
        StoryPlaybackState state = resolvePlaybackState(robotId);
        if (state == null) {
            return null;
        }

        Story story = storyRepository.findById(state.storyId()).orElse(null);
        if (story == null) {
            return null;
        }

        List<StorySegmentSnapshot> segments = getStorySegments(state.storyId());
        List<String> visibleSegments = segments.stream()
                .filter(segment -> segment.segmentOrder() <= state.currentSegmentOrder())
                .map(segment -> "Doan " + segment.segmentOrder() + ": " + segment.content())
                .toList();

        if (visibleSegments.isEmpty()) {
            return null;
        }

        int safeRecentCount = Math.max(1, Math.min(10, recentSegmentCount));
        int fromIndex = Math.max(0, visibleSegments.size() - safeRecentCount);
        String context = String.join("\n", visibleSegments.subList(fromIndex, visibleSegments.size()));

        return StoryQaContext.builder()
                .storyId(story.getId())
                .storyTitle(story.getTitle())
                .currentSegmentOrder(state.currentSegmentOrder())
                .recentContext(context)
                .build();
    }

    private StoryPlaybackAudioChunk buildPlaybackResponse(UUID robotId, Story story, StorySegmentSnapshot segment, boolean completed) {
        TtsAudioResult tts = getSegmentAudio(segment);

        return StoryPlaybackAudioChunk.builder()
                .robotId(robotId)
                .storyId(story.getId())
                .storyTitle(story.getTitle())
                .segmentId(segment.segmentId())
                .segmentOrder(segment.segmentOrder())
                .content(segment.content())
                .emotion(segment.emotion())
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

        StoryPlaybackState fromRedis = getPlaybackStateFromRedis(robotId);
        if (fromRedis != null) {
            playbackStates.put(robotId, fromRedis);
            return fromRedis;
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
        savePlaybackStateToRedis(robotId, recovered);
        return recovered;
    }

    private void savePlaybackState(UUID robotId, Story story, Integer segmentOrder) {
        StoryPlaybackStateEntity state = new StoryPlaybackStateEntity();
        state.setRobotId(robotId);
        state.setStory(story);
        state.setCurrentSegmentOrder(segmentOrder);
        storyPlaybackStateRepository.save(state);
        savePlaybackStateToRedis(robotId, new StoryPlaybackState(story.getId(), segmentOrder));
    }

    private void clearPlaybackState(UUID robotId) {
        playbackStates.remove(robotId);
        storyPlaybackStateRepository.deleteById(robotId);
        stringRedisTemplate.delete(playbackStateKey(robotId));
    }

    private List<StorySegmentSnapshot> getStorySegments(UUID storyId) {
        List<StorySegmentSnapshot> inMemory = storySegmentsCache.get(storyId);
        if (inMemory != null && !inMemory.isEmpty()) {
            return inMemory;
        }

        List<StorySegmentSnapshot> fromRedis = getStorySegmentsFromRedis(storyId);
        if (fromRedis != null && !fromRedis.isEmpty()) {
            storySegmentsCache.put(storyId, fromRedis);
            return fromRedis;
        }

        List<StorySegment> segments = storySegmentRepository.findByStoryIdOrderBySegmentOrderAsc(storyId);
        if (segments.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "STORY_EMPTY", "Story has no segments");
        }

        List<StorySegmentSnapshot> loaded = segments.stream()
                .sorted(Comparator.comparing(StorySegment::getSegmentOrder))
                .map(segment -> new StorySegmentSnapshot(
                        segment.getId(),
                        segment.getSegmentOrder(),
                        segment.getContent(),
                        segment.getEmotion()
                ))
                .toList();

        storySegmentsCache.put(storyId, loaded);
        saveStorySegmentsToRedis(storyId, loaded);
        return loaded;
    }

    private StorySegmentSnapshot findNextSegment(List<StorySegmentSnapshot> segments, Integer currentOrder) {
        for (StorySegmentSnapshot segment : segments) {
            if (segment.segmentOrder() > currentOrder) {
                return segment;
            }
        }
        return null;
    }

    private void prefetchNextAudio(Integer currentOrder, List<StorySegmentSnapshot> segments) {
        StorySegmentSnapshot next = findNextSegment(segments, currentOrder);
        if (next == null || segmentAudioCache.containsKey(next.segmentId())) {
            return;
        }
        CompletableFuture.runAsync(() -> getSegmentAudio(next), playbackPrefetchExecutor);
    }

    private TtsAudioResult getSegmentAudio(StorySegmentSnapshot segment) {
        TtsAudioResult inMemory = segmentAudioCache.get(segment.segmentId());
        if (inMemory != null) {
            return inMemory;
        }

        TtsAudioResult fromRedis = getSegmentAudioFromRedis(segment.segmentId());
        if (fromRedis != null) {
            segmentAudioCache.put(segment.segmentId(), fromRedis);
            return fromRedis;
        }

        TtsAudioResult generated = ttsService.synthesize(segment.content());
        segmentAudioCache.put(segment.segmentId(), generated);
        saveSegmentAudioToRedis(segment.segmentId(), generated);
        return generated;
    }

    private void savePlaybackStateToRedis(UUID robotId, StoryPlaybackState state) {
        try {
            String payload = objectMapper.writeValueAsString(state);
            stringRedisTemplate.opsForValue().set(playbackStateKey(robotId), payload, PLAYBACK_STATE_TTL);
        } catch (Exception ignored) {
        }
    }

    private StoryPlaybackState getPlaybackStateFromRedis(UUID robotId) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(playbackStateKey(robotId));
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return objectMapper.readValue(payload, StoryPlaybackState.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveStorySegmentsToRedis(UUID storyId, List<StorySegmentSnapshot> segments) {
        try {
            String payload = objectMapper.writeValueAsString(segments);
            stringRedisTemplate.opsForValue().set(storySegmentsKey(storyId), payload, SEGMENTS_TTL);
        } catch (Exception ignored) {
        }
    }

    private List<StorySegmentSnapshot> getStorySegmentsFromRedis(UUID storyId) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(storySegmentsKey(storyId));
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return objectMapper.readValue(payload, new TypeReference<List<StorySegmentSnapshot>>() {
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveSegmentAudioToRedis(UUID segmentId, TtsAudioResult audio) {
        try {
            if (audio == null || audio.getAudioBytes() == null || audio.getAudioBytes().length == 0) {
                return;
            }

            SegmentAudioCachePayload payload = new SegmentAudioCachePayload(
                    Base64.getEncoder().encodeToString(audio.getAudioBytes()),
                    audio.getMimeType(),
                    audio.getSampleRate(),
                    audio.getChannels()
            );

            String serialized = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.opsForValue().set(segmentAudioKey(segmentId), serialized, SEGMENT_AUDIO_TTL);
        } catch (Exception ignored) {
        }
    }

    private TtsAudioResult getSegmentAudioFromRedis(UUID segmentId) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(segmentAudioKey(segmentId));
            if (payload == null || payload.isBlank()) {
                return null;
            }

            SegmentAudioCachePayload cached = objectMapper.readValue(payload, SegmentAudioCachePayload.class);
            if (cached.audioBase64() == null || cached.audioBase64().isBlank()) {
                return null;
            }

            return TtsAudioResult.builder()
                    .audioBytes(Base64.getDecoder().decode(cached.audioBase64()))
                    .mimeType(cached.mimeType())
                    .sampleRate(cached.sampleRate())
                    .channels(cached.channels())
                    .build();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String playbackStateKey(UUID robotId) {
        return REDIS_PLAYBACK_STATE_KEY_PREFIX + robotId;
    }

    private String storySegmentsKey(UUID storyId) {
        return REDIS_SEGMENTS_KEY_PREFIX + storyId;
    }

    private String segmentAudioKey(UUID segmentId) {
        return REDIS_AUDIO_KEY_PREFIX + segmentId;
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

    @PreDestroy
    void shutdownPrefetchExecutor() {
        playbackPrefetchExecutor.shutdown();
    }

    private record StorySegmentSnapshot(UUID segmentId, Integer segmentOrder, String content, EmotionType emotion) {
    }

    private record SegmentAudioCachePayload(String audioBase64, String mimeType, Integer sampleRate, Integer channels) {
    }

    private record StoryPlaybackState(UUID storyId, Integer currentSegmentOrder) {
    }
}

