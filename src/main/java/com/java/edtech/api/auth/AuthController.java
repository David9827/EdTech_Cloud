package com.java.edtech.api.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import com.java.edtech.api.auth.dto.LoginRequest;
import com.java.edtech.api.auth.dto.RegisterRequest;
import com.java.edtech.api.auth.dto.RefreshRequest;
import com.java.edtech.api.auth.dto.TokenResponse;
import com.java.edtech.service.auth.AuthService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }
}
