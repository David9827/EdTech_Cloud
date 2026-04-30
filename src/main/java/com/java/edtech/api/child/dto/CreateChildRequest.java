package com.java.edtech.api.child.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateChildRequest {
    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @PastOrPresent(message = "birthDate must be in the past or present")
    private LocalDate birthDate;

    @Size(max = 500, message = "avatarUrl must be at most 500 characters")
    private String avatarUrl;
}
