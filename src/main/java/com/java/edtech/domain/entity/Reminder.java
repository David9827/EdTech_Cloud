package com.java.edtech.domain.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.java.edtech.domain.enums.ReminderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reminder")
@Getter
@Setter
public class Reminder {
    private static final ZoneId REMINDER_LOCAL_ZONE = ZoneId.of("Asia/Bangkok");

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne
    @JoinColumn(name = "child_id")
    private Child child;

    @ManyToOne
    @JoinColumn(name = "robot_id")
    private Robot robot;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "schedule_at", nullable = false)
    private LocalDateTime scheduleAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "reminder_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ReminderStatus status = ReminderStatus.ACTIVE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now(REMINDER_LOCAL_ZONE);
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now(REMINDER_LOCAL_ZONE);
    }
}
