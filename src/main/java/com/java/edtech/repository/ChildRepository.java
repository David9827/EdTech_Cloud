package com.java.edtech.repository;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.java.edtech.domain.entity.Child;

public interface ChildRepository extends JpaRepository<Child, UUID> {
    List<Child> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
