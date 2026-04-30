package com.java.edtech.repository;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.java.edtech.domain.entity.Robot;

public interface RobotRepository extends JpaRepository<Robot, UUID> {
    @Query(
            value = "SELECT r.* FROM robot r " +
                    "JOIN user_robot ur ON ur.robot_id = r.id " +
                    "WHERE ur.user_id = :userId " +
                    "ORDER BY r.created_at DESC",
            nativeQuery = true
    )
    List<Robot> findByUserId(@Param("userId") UUID userId);

    @Modifying
    @Transactional
    @Query(
            value = "INSERT INTO user_robot (user_id, robot_id) VALUES (:userId, :robotId)",
            nativeQuery = true
    )
    int addUserRobot(@Param("userId") UUID userId, @Param("robotId") UUID robotId);
}
