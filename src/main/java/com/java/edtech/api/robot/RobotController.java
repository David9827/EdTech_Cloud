package com.java.edtech.api.robot;

import java.util.UUID;

import com.java.edtech.api.robot.dto.IssueStoryCommandRequest;
import com.java.edtech.api.robot.dto.RobotApiResponse;
import com.java.edtech.api.robot.dto.RobotStoryCommandResponse;
import com.java.edtech.service.robot.RobotStoryCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/robots")
@RequiredArgsConstructor
public class RobotController {
    private final RobotStoryCommandService robotStoryCommandService;

    @PostMapping("/{robotId}/story-command")
    public ResponseEntity<RobotApiResponse<RobotStoryCommandResponse>> issueStoryCommand(
            @PathVariable UUID robotId,
            @Valid @RequestBody IssueStoryCommandRequest request
    ) {
        return issueCommand(robotId, request);
    }

    @GetMapping("/{robotId}/story-command")
    public ResponseEntity<RobotApiResponse<RobotStoryCommandResponse>> pollStoryCommand(
            @PathVariable UUID robotId,
            @RequestParam(defaultValue = "true") boolean consume
    ) {
        return pullCommand(robotId, consume);
    }

    @PostMapping("/{robotId}/commands")
    public ResponseEntity<RobotApiResponse<RobotStoryCommandResponse>> issueCommand(
            @PathVariable UUID robotId,
            @Valid @RequestBody IssueStoryCommandRequest request
    ) {
        RobotStoryCommandResponse response = robotStoryCommandService.issueCommand(robotId, request);
        return ResponseEntity.ok(RobotApiResponse.ok("Command issued", response));
    }

    @GetMapping("/{robotId}/commands/pull")
    public ResponseEntity<RobotApiResponse<RobotStoryCommandResponse>> pullCommand(
            @PathVariable UUID robotId,
            @RequestParam(defaultValue = "true") boolean consume
    ) {
        RobotStoryCommandResponse response = robotStoryCommandService.pollCommand(robotId, consume);
        return ResponseEntity.ok(RobotApiResponse.ok("Command pulled", response));
    }
}
