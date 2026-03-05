package com.java.edtech.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.java.edtech.domain.entity.Robot;

public interface RobotRepository extends JpaRepository<Robot, UUID> {
}
