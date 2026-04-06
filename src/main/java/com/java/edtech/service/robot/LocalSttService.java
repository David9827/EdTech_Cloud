package com.java.edtech.service.robot;

import java.util.Map;

import com.java.edtech.service.robot.config.SttProperties;
import com.java.edtech.websocket.dto.AudioFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "stt", name = "provider", havingValue = "local")
public class LocalSttService implements SttService {
    private final SttProperties sttProperties;

    @Override
    public String transcribe(byte[] compressedAudioBytes, Integer sampleRate, Integer channels, AudioFormat format) {
        if (compressedAudioBytes == null || compressedAudioBytes.length == 0) {
            return "";
        }
        String normalizedFormat = format == null ? AudioFormat.OGG_OPUS.name() : format.name();
        final String extension = AudioFormat.PCM_16BIT.name().equals(normalizedFormat) ? "pcm" : "ogg";

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(sttProperties.getTimeoutMs());
        requestFactory.setReadTimeout(sttProperties.getTimeoutMs());

        RestClient client = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        ByteArrayResource audioResource = new ByteArrayResource(compressedAudioBytes) {
            @Override
            public String getFilename() {
                return "audio." + extension;
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("audio", audioResource);
        form.add("sample_rate", sampleRate == null ? 16000 : sampleRate);
        form.add("channels", channels == null ? 1 : channels);
        form.add("format", normalizedFormat);

        try {
            Map<?, ?> response = client.post()
                    .uri(sttProperties.getLocalUrl())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return "";
            }
            Object text = response.get("text");
            return text == null ? "" : text.toString();
        } catch (RestClientException ex) {
            return "STT local service error: " + ex.getMessage();
        }
    }
}
