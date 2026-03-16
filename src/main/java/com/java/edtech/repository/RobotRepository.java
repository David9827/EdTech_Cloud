package com.java.edtech.repository;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
