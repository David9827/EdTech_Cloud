package com.java.edtech.api.user;

import com.java.edtech.api.user.dto.ChangePasswordRequest;
import com.java.edtech.api.user.dto.DeleteAccountRequest;
import com.java.edtech.api.user.dto.UpdateAvatarRequest;
import com.java.edtech.api.user.dto.UserApiResponse;
import com.java.edtech.api.user.dto.UserProfileResponse;
import com.java.edtech.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserApiResponse<UserProfileResponse>> getProfile() {
        UserProfileResponse response = userService.getProfile();
        return ResponseEntity.ok(UserApiResponse.ok("User profile fetched", response));
    }

    @PatchMapping("/me/avatar")
    public ResponseEntity<UserApiResponse<Void>> updateAvatar(@Valid @RequestBody UpdateAvatarRequest request) {
        userService.updateAvatar(request);
        return ResponseEntity.ok(UserApiResponse.ok("Avatar updated successfully", null));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<UserApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(UserApiResponse.ok("Password changed successfully", null));
    }

    @DeleteMapping("/me")
    public ResponseEntity<UserApiResponse<Void>> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(request);
        return ResponseEntity.ok(UserApiResponse.ok("Account deleted successfully", null));
    }
}
