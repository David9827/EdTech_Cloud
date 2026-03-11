package com.java.edtech.api.reminder.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.java.edtech.domain.enums.ReminderStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReminderResponse {
    private UUID id;
    private UUID userId;
    private UUID childId;
    private UUID robotId;
    private String title;
    private String message;
    private LocalDateTime scheduleAt;
    private ReminderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime doneAt;
}
