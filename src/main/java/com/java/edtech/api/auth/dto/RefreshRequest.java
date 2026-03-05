package com.java.edtech.api.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class RefreshRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
