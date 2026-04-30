package com.java.edtech.api.robot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRobotRequest {
    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @NotBlank(message = "deviceKey is required")
    @Size(max = 255, message = "deviceKey must be at most 255 characters")
    private String deviceKey;

    @Size(max = 500, message = "imgUrl must be at most 500 characters")
    private String imgUrl;

    @Min(value = 0, message = "volume must be between 0 and 100")
    @Max(value = 100, message = "volume must be between 0 and 100")
    private Integer volume;
}
