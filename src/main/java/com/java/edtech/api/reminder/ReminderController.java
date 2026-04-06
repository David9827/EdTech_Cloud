package com.java.edtech.api.reminder;

import java.util.UUID;

import com.java.edtech.api.reminder.dto.CreateReminderRequest;
import com.java.edtech.api.reminder.dto.ReminderApiResponse;
import com.java.edtech.api.reminder.dto.ReminderExecuteDataResponse;
import com.java.edtech.api.reminder.dto.ReminderPageResponse;
import com.java.edtech.api.reminder.dto.ReminderResponse;
import com.java.edtech.service.reminder.ReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PatchMapping("/{reminderId}/cancel")
    public ResponseEntity<ReminderApiResponse<ReminderResponse>> cancelReminder(@PathVariable UUID reminderId) {
        ReminderResponse response = reminderService.cancelReminder(reminderId);
        return ResponseEntity.ok(ReminderApiResponse.ok("Reminder cancelled", response));
    }
}
