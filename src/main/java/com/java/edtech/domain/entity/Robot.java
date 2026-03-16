package com.java.edtech.domain.entity;

import java.util.UUID;
import java.time.Instant;

import com.java.edtech.domain.enums.RobotStatus;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "robot")
@Getter
@Setter
public class Robot {
    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "device_key", nullable = false, length = 255)
    private String deviceKey;

    @Column(name = "img_url", length = 500)
    private String imgUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "robot_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private RobotStatus status = RobotStatus.IDLE;

    @Column(nullable = false)
    private Integer volume;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
