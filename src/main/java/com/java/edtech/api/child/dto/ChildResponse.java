package com.java.edtech.api.child.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChildResponse {
    private UUID id;
    private String name;
    private Integer age;
    private String avatarUrl;
}
