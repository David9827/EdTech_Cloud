package com.java.edtech.domain.entity;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "child")
@Getter
@Setter
public class Child {
    @Id
    private UUID id;
}
