package com.java.edtech.service.reminder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.java.edtech.api.robot.dto.IssueStoryCommandRequest;
import com.java.edtech.domain.entity.Reminder;
import com.java.edtech.domain.enums.ReminderStatus;
import com.java.edtech.domain.enums.RobotCommandType;
import com.java.edtech.repository.ReminderRepository;
import com.java.edtech.service.robot.RobotStoryCommandService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReminderCommandDispatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReminderCommandDispatchScheduler.class);
    private static final ZoneId REMINDER_LOCAL_ZONE = ZoneId.of("Asia/Bangkok");

    private final ReminderRepository reminderRepository;
    private final RobotStoryCommandService robotStoryCommandService;

    @Scheduled(fixedDelayString = "${reminder.dispatch.fixed-delay-ms:5000}")
    public void dispatchDueReminders() {
        List<Reminder> dueReminders = reminderRepository
                .findByStatusAndScheduleAtLessThanEqualAndRobotIsNotNull(
                        ReminderStatus.ACTIVE,
                        LocalDateTime.now(REMINDER_LOCAL_ZONE)
                );
        for (Reminder reminder : dueReminders) {
            try {
                dispatchSingleReminder(reminder);
            } catch (Exception ex) {
                log.error("Dispatch reminder failed reminderId={} robotId={}",
                        reminder.getId(),
                        reminder.getRobot() == null ? null : reminder.getRobot().getId(),
                        ex);
            }
        }
    }

    @Transactional
    protected void dispatchSingleReminder(Reminder reminder) {
        if (reminder.getStatus() != ReminderStatus.ACTIVE) {
            return;
        }
        if (reminder.getRobot() == null) {
            return;
        }

        IssueStoryCommandRequest request = new IssueStoryCommandRequest();
        request.setType(RobotCommandType.REMINDER_CREATE);
        request.setReminderId(reminder.getId());

        robotStoryCommandService.issueCommand(reminder.getRobot().getId(), request);

        reminder.setStatus(ReminderStatus.DONE);
        reminder.setDoneAt(LocalDateTime.now(REMINDER_LOCAL_ZONE));
        reminderRepository.save(reminder);
        log.info("Dispatched reminder command reminderId={} robotId={}",
                reminder.getId(), reminder.getRobot().getId());
    }
}
