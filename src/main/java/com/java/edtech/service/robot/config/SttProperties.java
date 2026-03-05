package com.java.edtech.service.robot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "stt")
public class SttProperties {
    private String provider = "local";
    private String localUrl = "http://127.0.0.1:8001/transcribe";
    private int timeoutMs = 30000;
}
