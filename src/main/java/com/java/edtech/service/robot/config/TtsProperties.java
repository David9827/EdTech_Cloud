package com.java.edtech.service.robot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tts")
public class TtsProperties {
    private String provider = "local";
    private String localUrl = "http://127.0.0.1:8002/synthesize";
    private int timeoutMs = 30000;
}
