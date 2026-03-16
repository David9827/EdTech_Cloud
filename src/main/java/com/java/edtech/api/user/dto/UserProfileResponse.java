package com.java.edtech.api.user.dto;

import java.time.Instant;

import lombok.Data;

@Data
public class UserProfileResponse {
    private String id;
    private String email;
    private String phone;
    private String fullName;
    private String bio;
    private String avatarUrl;
    private String role;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
