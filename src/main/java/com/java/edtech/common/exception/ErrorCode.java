package com.java.edtech.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "New password and confirm password do not match",
            "New password confirmation does not match."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials", "Email/phone or password is incorrect."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found", "The requested user could not be found."),
    USER_INACTIVE(HttpStatus.FORBIDDEN, "User is inactive", "Your account is inactive. Please contact support."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized", "Please sign in to continue."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied", "You do not have permission to access this resource."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid token", "Your session token is invalid. Please sign in again."),
    STORY_CONTENT_EMPTY(HttpStatus.BAD_REQUEST, "Story content is empty after normalization",
            "Story content is empty. Please add content and try again."),
    DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "Draft story not found", "Draft story was not found."),
    TITLE_REQUIRED(HttpStatus.BAD_REQUEST, "title is required", "Please provide a story title."),
    CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "content is required", "Please provide story content."),
    ROBOT_NOT_FOUND(HttpStatus.NOT_FOUND, "Robot not found", "Robot was not found."),
    STORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Story not found", "Story was not found."),
    PLAYBACK_NOT_STARTED(HttpStatus.BAD_REQUEST, "Story playback is not started",
            "Playback has not started yet."),
    STORY_EMPTY(HttpStatus.BAD_REQUEST, "Story has no segments", "Story has no playable segments."),
    EMAIL_EXISTS(HttpStatus.CONFLICT, "Email already exists", "This email is already registered."),
    PHONE_EXISTS(HttpStatus.CONFLICT, "Phone already exists", "This phone number is already registered."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid refresh token",
            "Refresh token is invalid. Please sign in again."),
    REFRESH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Refresh token revoked",
            "Your session has been revoked. Please sign in again."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh token expired",
            "Your session has expired. Please sign in again."),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "Child not found", "Child profile was not found."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation session not found",
            "Conversation session was not found."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "Invalid cursor", "The pagination cursor is invalid."),
    SCHEDULE_AT_INVALID(HttpStatus.BAD_REQUEST, "scheduleAt must be in the future (Asia/Bangkok)",
            "Reminder time must be in the future."),
    REMINDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Reminder not found", "Reminder was not found."),
    REMINDER_CANCELLED(HttpStatus.GONE, "Reminder has been cancelled", "This reminder was cancelled."),
    REMINDER_ALREADY_DONE(HttpStatus.BAD_REQUEST, "Reminder already completed",
            "This reminder is already completed."),
    COMMAND_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "type or action is required",
            "Command type is required."),
    STORY_ID_REQUIRED(HttpStatus.BAD_REQUEST, "storyId is required for START_STORY command",
            "Story ID is required for this command."),
    REMINDER_ID_REQUIRED(HttpStatus.BAD_REQUEST, "reminderId is required for reminder command",
            "Reminder ID is required for this command."),
    REDIS_WRITE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save story command",
            "System is busy. Please try again."),
    REDIS_READ_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read story command",
            "System is busy. Please try again."),

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed", "Some fields are invalid. Please review them."),
    REQUEST_BODY_INVALID(HttpStatus.BAD_REQUEST, "Malformed request body",
            "Request body format is invalid."),
    PARAMETER_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "Request parameter type mismatch",
            "One or more request parameters have invalid format."),
    CONSTRAINT_VIOLATION(HttpStatus.BAD_REQUEST, "Constraint violation",
            "Request constraints are not satisfied."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "Data integrity violation",
            "The submitted data conflicts with existing records."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
            "Unexpected server error. Please try again later.");

    private final HttpStatus status;
    private final String message;
    private final String userMessage;

    ErrorCode(HttpStatus status, String message, String userMessage) {
        this.status = status;
        this.message = message;
        this.userMessage = userMessage;
    }

    public String getCode() {
        return name();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
