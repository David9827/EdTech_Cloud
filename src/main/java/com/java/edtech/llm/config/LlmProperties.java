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
    private int maxRetries = 2;
    private long retryBackoffMs = 1000;
    private String fallbackMessage = "Xin loi con, he thong dang ban. Con thu lai sau it giay nhe";
    private String storyFallbackMessage = "Bay gio robot ke tiep cau chuyen ngan gon nhe";
}
