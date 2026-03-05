package com.java.edtech.service.robot;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AudioPipelineResult {
    private String transcript;
    private String assistantReply;
    private String error;
}
