package com.java.edtech.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private boolean enabled = false;
    private String provider;
    private String model;
    private String apiKey;
    private String baseUrl;
    private int timeoutMs = 15000;
    private int maxRetries = 2;
    private long retryBackoffMs = 1000;
    private String fallbackMessage ;
    private String storyFallbackMessage;
}
