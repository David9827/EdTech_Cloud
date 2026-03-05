package com.java.edtech.service.robot;

import java.util.Map;

import com.java.edtech.service.robot.config.TtsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tts", name = "provider", havingValue = "local")
public class LocalTtsService implements TtsService {
    private final TtsProperties ttsProperties;

    @Override
    public TtsAudioResult synthesize(String text) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(ttsProperties.getTimeoutMs());
        requestFactory.setReadTimeout(ttsProperties.getTimeoutMs());

        RestClient client = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        try {
            byte[] audioBytes = client.post()
                    .uri(ttsProperties.getLocalUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .body(Map.of("text", text == null ? "" : text))
                    .retrieve()
                    .body(byte[].class);
            if (audioBytes == null) {
                audioBytes = new byte[0];
            }
            return TtsAudioResult.builder()
                    .audioBytes(audioBytes)
                    .mimeType("audio/wav")
                    .sampleRate(16000)
                    .channels(1)
                    .build();
        } catch (RestClientException ex) {
            return TtsAudioResult.builder()
                    .audioBytes(new byte[0])
                    .mimeType("audio/wav")
                    .sampleRate(16000)
                    .channels(1)
                    .build();
        }
    }
}
