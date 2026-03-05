package com.java.edtech.domain.entity;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "robot")
@Getter
@Setter
public class Robot {
    @Id
    private UUID id;
}
