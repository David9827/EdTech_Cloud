package com.java.edtech.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private boolean enabled = false;
    private String provider = "gemini";
    private String model = "gemini-2.0-flash";
    private String apiKey;
    private String baseUrl;
    private int timeoutMs = 15000;
}
