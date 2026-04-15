package com.java.edtech.api.reminder;

import java.util.UUID;

import com.java.edtech.api.reminder.dto.CreateReminderRequest;
import com.java.edtech.api.reminder.dto.ReminderApiResponse;
import com.java.edtech.api.reminder.dto.ReminderExecuteDataResponse;
import com.java.edtech.api.reminder.dto.ReminderPageResponse;
import com.java.edtech.api.reminder.dto.ReminderResponse;
import com.java.edtech.service.robot.TtsAudioResult;
import com.java.edtech.service.reminder.ReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {
    private final ReminderService reminderService;

    @PostMapping
    public ResponseEntity<ReminderApiResponse<ReminderResponse>> createReminder(
            @Valid @RequestBody CreateReminderRequest request
    ) {
        ReminderResponse response = reminderService.createReminder(request);
        return ResponseEntity.ok(ReminderApiResponse.ok("Reminder created", response));
    }

    @GetMapping
    public ResponseEntity<ReminderApiResponse<ReminderPageResponse>> listReminders(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ReminderPageResponse response = reminderService.listReminders(userId, page, size);
        return ResponseEntity.ok(ReminderApiResponse.ok("Reminders fetched", response));
    }

    @GetMapping("/{reminderId}")
    public ResponseEntity<ReminderApiResponse<ReminderResponse>> getReminder(@PathVariable UUID reminderId) {
        ReminderResponse response = reminderService.getReminder(reminderId);
        return ResponseEntity.ok(ReminderApiResponse.ok("Reminder fetched", response));
    }

    @GetMapping("/{reminderId}/execute-data")
    public ResponseEntity<ReminderApiResponse<ReminderExecuteDataResponse>> getExecuteData(@PathVariable UUID reminderId) {
        ReminderExecuteDataResponse response = reminderService.getExecuteData(reminderId);
        return ResponseEntity.ok(ReminderApiResponse.ok("Reminder execute data fetched", response));
    }

    @GetMapping("/{reminderId}/execute-audio")
    public ResponseEntity<byte[]> getExecuteAudio(@PathVariable UUID reminderId) {
        TtsAudioResult audio = reminderService.getExecuteAudio(reminderId);
        HttpHeaders headers = new HttpHeaders();

        String mime = audio.getMimeType() == null ? "audio/wav" : audio.getMimeType();
        headers.setContentType(MediaType.parseMediaType(mime));
        if (audio.getSampleRate() != null) {
            headers.set("X-Sample-Rate", String.valueOf(audio.getSampleRate()));
        }
        if (audio.getChannels() != null) {
            headers.set("X-Channels", String.valueOf(audio.getChannels()));
        }

        byte[] payload = audio.getAudioBytes() == null ? new byte[0] : audio.getAudioBytes();
        if (payload.length == 0) {
            return new ResponseEntity<>(payload, headers, HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(payload, headers, HttpStatus.OK);
    }

    @PatchMapping("/{reminderId}/cancel")
    public ResponseEntity<ReminderApiResponse<ReminderResponse>> cancelReminder(@PathVariable UUID reminderId) {
        ReminderResponse response = reminderService.cancelReminder(reminderId);
        return ResponseEntity.ok(ReminderApiResponse.ok("Reminder cancelled", response));
    }
}
