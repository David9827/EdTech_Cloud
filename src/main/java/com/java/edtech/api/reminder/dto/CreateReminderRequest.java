package com.java.edtech.api.reminder.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReminderRequest {
    @NotNull(message = "userId is required")
    private UUID userId;

    private UUID childId;
    private UUID robotId;

    @NotBlank(message = "title is required")
    private String title;

    private String message;

    @NotNull(message = "scheduleAt is required")
    private LocalDateTime scheduleAt;
}
