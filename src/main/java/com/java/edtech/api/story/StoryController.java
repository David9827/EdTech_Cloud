package com.java.edtech.api.story;

import java.util.List;
import java.util.UUID;

import com.java.edtech.api.story.dto.CreateStoryRequest;
import com.java.edtech.api.story.dto.CreateStoryResponse;
import com.java.edtech.api.story.dto.NextStoryPlaybackRequest;
import com.java.edtech.api.story.dto.StartStoryPlaybackRequest;
import com.java.edtech.api.story.dto.StopStoryPlaybackRequest;
import com.java.edtech.api.story.dto.StoryApiResponse;
import com.java.edtech.api.story.dto.StoryDraftDetailResponse;
import com.java.edtech.api.story.dto.UpdateDraftStoryRequest;
import com.java.edtech.api.story.dto.StoryPlaybackAudioChunk;
import com.java.edtech.api.story.dto.StoryPlaybackResponse;
import com.java.edtech.api.story.dto.StorySegmentResponse;
import com.java.edtech.api.story.dto.StoryListResponse;
import com.java.edtech.api.story.dto.StorySummaryResponse;
import com.java.edtech.domain.enums.StoryStatus;
import com.java.edtech.service.story.StorySegmentService;
import com.java.edtech.service.story.StoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {
    private final StoryService storyService;
    private final StorySegmentService storySegmentService;

    @PostMapping
    public ResponseEntity<StoryApiResponse<CreateStoryResponse>> createStory(
            @Valid @RequestBody CreateStoryRequest request
    ) {
        CreateStoryResponse data = storyService.createStory(request);
        return ResponseEntity.ok(StoryApiResponse.ok("Story created and segmented successfully", data));
    }

    @GetMapping("/{storyId}/segments")
    public ResponseEntity<StoryApiResponse<List<StorySegmentResponse>>> getStorySegments(@PathVariable UUID storyId) {
        List<StorySegmentResponse> data = storySegmentService.getSegments(storyId);
        return ResponseEntity.ok(StoryApiResponse.ok("Story segments fetched successfully", data));
    }

    @GetMapping
    public ResponseEntity<StoryApiResponse<StoryListResponse>> listStories(
            @RequestParam(required = false) StoryStatus status
    ) {
        List<StorySummaryResponse> data = storyService.listStories(status);
        StoryListResponse payload = StoryListResponse.builder()
                .total(data.size())
                .items(data)
                .build();
        return ResponseEntity.ok(StoryApiResponse.ok("Stories fetched successfully", payload));
    }

    @GetMapping("/drafts/{storyId}")
    public ResponseEntity<StoryApiResponse<StoryDraftDetailResponse>> getDraftWithContent(
            @PathVariable UUID storyId
    ) {
        StoryDraftDetailResponse response = storyService.getDraftWithContent(storyId);
        return ResponseEntity.ok(StoryApiResponse.ok("Draft story fetched successfully", response));
    }

    @PatchMapping("/drafts/{storyId}")
    public ResponseEntity<StoryApiResponse<StoryDraftDetailResponse>> updateDraft(
            @PathVariable UUID storyId,
            @RequestBody UpdateDraftStoryRequest request
    ) {
        StoryDraftDetailResponse response = storyService.updateDraft(storyId, request);
        return ResponseEntity.ok(StoryApiResponse.ok("Draft story updated successfully", response));
    }

    @GetMapping("/search")
    public ResponseEntity<StoryApiResponse<StoryListResponse>> searchStories(
            @RequestParam String query,
            @RequestParam(required = false) StoryStatus status
    ) {
        List<StorySummaryResponse> data = storyService.searchStories(query, status);
        StoryListResponse payload = StoryListResponse.builder()
                .total(data.size())
                .items(data)
                .build();
        return ResponseEntity.ok(StoryApiResponse.ok("Stories search completed", payload));
    }

    @PostMapping("/playback/start")
    public ResponseEntity<byte[]> startPlayback(
            @Valid @RequestBody StartStoryPlaybackRequest request
    ) {
        StoryPlaybackAudioChunk chunk = storyService.startPlayback(request.getRobotId(), request.getStoryId());
        return toAudioResponse(chunk);
    }

    @PostMapping("/playback/next")
    public ResponseEntity<byte[]> nextPlayback(
            @Valid @RequestBody NextStoryPlaybackRequest request
    ) {
        StoryPlaybackAudioChunk chunk = storyService.nextPlayback(request.getRobotId());
        return toAudioResponse(chunk);
    }

    @PostMapping("/playback/stop")
    public ResponseEntity<StoryApiResponse<StoryPlaybackResponse>> stopPlayback(
            @Valid @RequestBody StopStoryPlaybackRequest request
    ) {
        StoryPlaybackResponse data = storyService.stopPlayback(request.getRobotId());
        return ResponseEntity.ok(StoryApiResponse.ok("Story playback stopped", data));
    }

    private ResponseEntity<byte[]> toAudioResponse(StoryPlaybackAudioChunk chunk) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Robot-Id", String.valueOf(chunk.getRobotId()));
        if (chunk.getStoryId() != null) {
            headers.set("X-Story-Id", String.valueOf(chunk.getStoryId()));
        }
        headers.set("X-Completed", String.valueOf(chunk.isCompleted()));
        if (chunk.getSegmentId() != null) {
            headers.set("X-Segment-Id", String.valueOf(chunk.getSegmentId()));
        }
        if (chunk.getSegmentOrder() != null) {
            headers.set("X-Segment-Order", String.valueOf(chunk.getSegmentOrder()));
        }
        if (chunk.getSampleRate() != null) {
            headers.set("X-Sample-Rate", String.valueOf(chunk.getSampleRate()));
        }
        if (chunk.getChannels() != null) {
            headers.set("X-Channels", String.valueOf(chunk.getChannels()));
        }

        String mime = chunk.getAudioMimeType() == null ? "audio/wav" : chunk.getAudioMimeType();
        headers.setContentType(MediaType.parseMediaType(mime));
        byte[] audio = chunk.getAudioBytes() == null ? new byte[0] : chunk.getAudioBytes();
        if (audio.length == 0 && chunk.isCompleted()) {
            return new ResponseEntity<>(audio, headers, HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(audio, headers, HttpStatus.OK);
    }
}
