package com.java.edtech.api.robot.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.java.edtech.domain.enums.RobotCommandType;
import com.java.edtech.domain.enums.RobotStoryCommandAction;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IssueStoryCommandRequest {
    // Legacy field for backward compatibility.
    private RobotStoryCommandAction action;
    // Preferred field for new robot command flow.
    private RobotCommandType type;
    private UUID storyId;
    @JsonIgnore
    private UUID reminderId;

    @AssertTrue(message = "type or action is required")
    public boolean isCommandPresent() {
        return type != null || action != null;
    }
}
