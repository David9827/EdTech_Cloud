package com.java.edtech.api.reminder.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReminderExecuteDataResponse {
    private UUID reminderId;
    private UUID robotId;
    private UUID childId;
    private String title;
    private String message;
    private LocalDateTime scheduleAt;
}
