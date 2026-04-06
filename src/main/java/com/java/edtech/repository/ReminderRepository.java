package com.java.edtech.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.java.edtech.domain.entity.Reminder;
import com.java.edtech.domain.enums.ReminderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {
    List<Reminder> findByStatusAndScheduleAtLessThanEqual(ReminderStatus status, LocalDateTime scheduledBeforeOrAt);

    List<Reminder> findByStatusAndScheduleAtLessThanEqualAndRobotIsNotNull(ReminderStatus status, LocalDateTime scheduledBeforeOrAt);

    @Query("""
            select r from Reminder r
            where r.user.id = :userId
            order by r.scheduleAt desc
            """)
    Page<Reminder> findByUserId(
            @Param("userId") UUID userId,
            Pageable pageable
    );
}
