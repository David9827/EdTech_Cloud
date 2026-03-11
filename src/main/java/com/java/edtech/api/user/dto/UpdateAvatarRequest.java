package com.java.edtech.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAvatarRequest {
    @NotBlank(message = "Avatar URL is required")
    @Size(max = 500, message = "Avatar URL must be at most 500 characters")
    private String avatarUrl;
}
