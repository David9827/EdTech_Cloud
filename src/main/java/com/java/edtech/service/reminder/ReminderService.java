package com.java.edtech.service.reminder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.java.edtech.service.robot.TtsAudioResult;
import com.java.edtech.service.robot.TtsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReminderService {
    private static final ZoneId REMINDER_LOCAL_ZONE = ZoneId.of("Asia/Bangkok");
    private static final String REDIS_REMINDER_AUDIO_KEY_PREFIX = "reminder:audio:";
    private static final Duration REMINDER_AUDIO_TTL = Duration.ofHours(2);

    private final ReminderRepository reminderRepository;
    private final AppUserRepository appUserRepository;
    private final ChildRepository childRepository;
    private final RobotRepository robotRepository;
    private final TtsService ttsService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<UUID, TtsAudioResult> reminderAudioCache = new ConcurrentHashMap<>();

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
        prefetchReminderAudio(saved);
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
        Reminder reminder = resolveExecutableReminder(reminderId);

        return ReminderExecuteDataResponse.builder()
                .reminderId(reminder.getId())
                .robotId(reminder.getRobot() == null ? null : reminder.getRobot().getId())
                .childId(reminder.getChild() == null ? null : reminder.getChild().getId())
                .title(reminder.getTitle())
                .message(reminder.getMessage())
                .scheduleAt(reminder.getScheduleAt())
                .build();
    }

    @Transactional(readOnly = true)
    public TtsAudioResult getExecuteAudio(UUID reminderId) {
        Reminder reminder = resolveExecutableReminder(reminderId);
        return getReminderAudio(reminder);
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

    private Reminder resolveExecutableReminder(UUID reminderId) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new AppException(ErrorCode.REMINDER_NOT_FOUND));
        if (reminder.getStatus() == ReminderStatus.CANCELLED) {
            throw new AppException(ErrorCode.REMINDER_CANCELLED);
        }
        return reminder;
    }

    private void prefetchReminderAudio(Reminder reminder) {
        try {
            getReminderAudio(reminder);
        } catch (Exception ignored) {
        }
    }

    private TtsAudioResult getReminderAudio(Reminder reminder) {
        TtsAudioResult inMemory = reminderAudioCache.get(reminder.getId());
        if (inMemory != null) {
            return inMemory;
        }

        TtsAudioResult fromRedis = getReminderAudioFromRedis(reminder.getId());
        if (fromRedis != null) {
            reminderAudioCache.put(reminder.getId(), fromRedis);
            return fromRedis;
        }

        String speechText = buildReminderSpeechText(reminder);
        if (speechText.isBlank()) {
            TtsAudioResult empty = emptyAudio();
            reminderAudioCache.put(reminder.getId(), empty);
            return empty;
        }

        TtsAudioResult generated = ttsService.synthesize(speechText);
        reminderAudioCache.put(reminder.getId(), generated);
        saveReminderAudioToRedis(reminder.getId(), generated);
        return generated;
    }

    private String buildReminderSpeechText(Reminder reminder) {
        String title = reminder.getTitle() == null ? "" : reminder.getTitle().trim();
        String message = reminder.getMessage() == null ? "" : reminder.getMessage().trim();
        if (!title.isEmpty() && !message.isEmpty()) {
            return title + ". " + message;
        }
        return title.isEmpty() ? message : title;
    }

    private void saveReminderAudioToRedis(UUID reminderId, TtsAudioResult audio) {
        try {
            if (audio == null || audio.getAudioBytes() == null || audio.getAudioBytes().length == 0) {
                return;
            }

            ReminderAudioCachePayload payload = new ReminderAudioCachePayload(
                    Base64.getEncoder().encodeToString(audio.getAudioBytes()),
                    audio.getMimeType(),
                    audio.getSampleRate(),
                    audio.getChannels()
            );

            String serialized = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.opsForValue().set(reminderAudioKey(reminderId), serialized, REMINDER_AUDIO_TTL);
        } catch (Exception ignored) {
        }
    }

    private TtsAudioResult getReminderAudioFromRedis(UUID reminderId) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(reminderAudioKey(reminderId));
            if (payload == null || payload.isBlank()) {
                return null;
            }

            ReminderAudioCachePayload cached = objectMapper.readValue(payload, ReminderAudioCachePayload.class);
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

    private String reminderAudioKey(UUID reminderId) {
        return REDIS_REMINDER_AUDIO_KEY_PREFIX + reminderId;
    }

    private TtsAudioResult emptyAudio() {
        return TtsAudioResult.builder()
                .audioBytes(new byte[0])
                .mimeType("audio/wav")
                .sampleRate(16000)
                .channels(1)
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

    private record ReminderAudioCachePayload(String audioBase64, String mimeType, Integer sampleRate, Integer channels) {
    }
}
