package com.java.edtech.service.reminder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.java.edtech.api.reminder.dto.CreateReminderRequest;
import com.java.edtech.api.reminder.dto.ReminderExecuteDataResponse;
import com.java.edtech.api.reminder.dto.ReminderPageResponse;
import com.java.edtech.api.reminder.dto.ReminderResponse;
import com.java.edtech.common.exception.AppException;
import com.java.edtech.common.exception.ErrorCode;
import com.java.edtech.domain.entity.AppUser;
import com.java.edtech.domain.entity.Child;
import com.java.edtech.domain.entity.Reminder;
import com.java.edtech.domain.entity.Robot;
import com.java.edtech.domain.enums.ReminderStatus;
import com.java.edtech.repository.AppUserRepository;
import com.java.edtech.repository.ChildRepository;
import com.java.edtech.repository.ReminderRepository;
import com.java.edtech.repository.RobotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReminderService {
    private static final ZoneId REMINDER_LOCAL_ZONE = ZoneId.of("Asia/Bangkok");

    private final ReminderRepository reminderRepository;
    private final AppUserRepository appUserRepository;
    private final ChildRepository childRepository;
    private final RobotRepository robotRepository;

    @Transactional
    public ReminderResponse createReminder(CreateReminderRequest request) {
        AppUser user = appUserRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Child child = null;
        if (request.getChildId() != null) {
            child = childRepository.findById(request.getChildId())
                    .orElseThrow(() -> new AppException(ErrorCode.CHILD_NOT_FOUND));
        }

        Robot robot = null;
        if (request.getRobotId() != null) {
            robot = robotRepository.findById(request.getRobotId())
                    .orElseThrow(() -> new AppException(ErrorCode.ROBOT_NOT_FOUND));
        }

        Reminder reminder = new Reminder();
        reminder.setId(UUID.randomUUID());
        reminder.setUser(user);
        reminder.setChild(child);
        reminder.setRobot(robot);
        reminder.setTitle(request.getTitle().trim());
        reminder.setMessage(request.getMessage());
        if (!request.getScheduleAt().isAfter(java.time.LocalDateTime.now(REMINDER_LOCAL_ZONE))) {
            throw new AppException(ErrorCode.SCHEDULE_AT_INVALID);
        }
        reminder.setScheduleAt(request.getScheduleAt());
        reminder.setStatus(ReminderStatus.ACTIVE);

        Reminder saved = reminderRepository.save(reminder);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ReminderResponse getReminder(UUID reminderId) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new AppException(ErrorCode.REMINDER_NOT_FOUND));
        return toResponse(reminder);
    }

    @Transactional(readOnly = true)
    public ReminderExecuteDataResponse getExecuteData(UUID reminderId) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new AppException(ErrorCode.REMINDER_NOT_FOUND));

        if (reminder.getStatus() == ReminderStatus.CANCELLED) {
            throw new AppException(ErrorCode.REMINDER_CANCELLED);
        }

        return ReminderExecuteDataResponse.builder()
                .reminderId(reminder.getId())
                .robotId(reminder.getRobot() == null ? null : reminder.getRobot().getId())
                .childId(reminder.getChild() == null ? null : reminder.getChild().getId())
                .title(reminder.getTitle())
                .message(reminder.getMessage())
                .scheduleAt(reminder.getScheduleAt())
                .build();
    }

    @Transactional
    public ReminderResponse cancelReminder(UUID reminderId) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new AppException(ErrorCode.REMINDER_NOT_FOUND));

        if (reminder.getStatus() == ReminderStatus.DONE) {
            throw new AppException(ErrorCode.REMINDER_ALREADY_DONE);
        }

        if (reminder.getStatus() != ReminderStatus.CANCELLED) {
            reminder.setStatus(ReminderStatus.CANCELLED);
            reminder.setCancelledAt(LocalDateTime.now(REMINDER_LOCAL_ZONE));
            reminder = reminderRepository.save(reminder);
        }
        return toResponse(reminder);
    }

    @Transactional(readOnly = true)
    public ReminderPageResponse listReminders(UUID userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "scheduleAt"));
        Page<Reminder> reminders = reminderRepository.findByUserId(userId, pageable);

        return ReminderPageResponse.builder()
                .items(reminders.getContent().stream().map(this::toResponse).toList())
                .page(reminders.getNumber())
                .size(reminders.getSize())
                .totalElements(reminders.getTotalElements())
                .totalPages(reminders.getTotalPages())
                .hasNext(reminders.hasNext())
                .build();
    }

    private ReminderResponse toResponse(Reminder reminder) {
        return ReminderResponse.builder()
                .id(reminder.getId())
                .userId(reminder.getUser() == null ? null : reminder.getUser().getId())
                .childId(reminder.getChild() == null ? null : reminder.getChild().getId())
                .robotId(reminder.getRobot() == null ? null : reminder.getRobot().getId())
                .title(reminder.getTitle())
                .message(reminder.getMessage())
                .scheduleAt(reminder.getScheduleAt())
                .status(reminder.getStatus())
                .createdAt(reminder.getCreatedAt())
                .updatedAt(reminder.getUpdatedAt())
                .cancelledAt(reminder.getCancelledAt())
                .doneAt(reminder.getDoneAt())
                .build();
    }
}
