package com.java.edtech.domain.entity;

import java.util.UUID;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "robot")
@Getter
@Setter
public class Robot {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "robot_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private RobotStatus status = RobotStatus.IDLE;
}
