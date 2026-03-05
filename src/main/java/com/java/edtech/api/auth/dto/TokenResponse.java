package com.java.edtech.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@JsonPropertyOrder({"success", "message", "data"})
@Data
public class TokenResponse {
    private boolean success;
    private String message;
    private TokenData data;

    @Data
    @JsonPropertyOrder({
            "accessToken",
            "refreshToken",
            "tokenType",
            "expiresInSeconds",
            "issuedAt",
            "user"
    })
    public static class TokenData {
        private String accessToken;
        private String refreshToken;
        private String tokenType = "Bearer";
        private long expiresInSeconds;
        private String issuedAt;
        private UserInfo user;
    }

    @Data
    @JsonPropertyOrder({"id", "email", "role"})
    public static class UserInfo {
        private String id;
        private String email;
        private String role;
    }
}
