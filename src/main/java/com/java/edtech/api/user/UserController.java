package com.java.edtech.api.user;

import java.util.List;

import com.java.edtech.api.child.dto.CreateChildRequest;
import com.java.edtech.api.child.dto.ChildResponse;
import com.java.edtech.api.robot.dto.CreateRobotRequest;
import com.java.edtech.api.user.dto.ChangePasswordRequest;
import com.java.edtech.api.user.dto.DeleteAccountRequest;
import com.java.edtech.api.user.dto.UpdateAvatarRequest;
import com.java.edtech.api.user.dto.UserApiResponse;
import com.java.edtech.api.user.dto.UserProfileResponse;
import com.java.edtech.api.robot.dto.RobotSummaryResponse;
import com.java.edtech.service.child.ChildService;
import com.java.edtech.service.robot.RobotService;
import com.java.edtech.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final RobotService robotService;
    private final ChildService childService;

    @GetMapping("/me")
    public ResponseEntity<UserApiResponse<UserProfileResponse>> getProfile() {
        UserProfileResponse response = userService.getProfile();
        return ResponseEntity.ok(UserApiResponse.ok("User profile fetched", response));
    }

    @GetMapping("/me/robots")
    public ResponseEntity<UserApiResponse<List<RobotSummaryResponse>>> getMyRobots() {
        List<RobotSummaryResponse> response = robotService.getMyRobots();
        return ResponseEntity.ok(UserApiResponse.ok("User robots fetched", response));
    }

    @PostMapping("/me/robots")
    public ResponseEntity<UserApiResponse<RobotSummaryResponse>> createMyRobot(
            @Valid @RequestBody CreateRobotRequest request
    ) {
        RobotSummaryResponse response = robotService.createMyRobot(request);
        return ResponseEntity.ok(UserApiResponse.ok("Robot created", response));
    }

    @GetMapping("/me/children")
    public ResponseEntity<UserApiResponse<List<ChildResponse>>> getMyChildren() {
        List<ChildResponse> response = childService.getMyChildren();
        return ResponseEntity.ok(UserApiResponse.ok("User children fetched", response));
    }

    @PostMapping("/me/children")
    public ResponseEntity<UserApiResponse<ChildResponse>> createMyChild(
            @Valid @RequestBody CreateChildRequest request
    ) {
        ChildResponse response = childService.createMyChild(request);
        return ResponseEntity.ok(UserApiResponse.ok("Child created", response));
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
