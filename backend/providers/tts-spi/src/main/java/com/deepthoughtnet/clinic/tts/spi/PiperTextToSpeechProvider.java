package com.deepthoughtnet.clinic.tts.spi;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP adapter for Piper runtime. Keeps synthesis runtime isolated from orchestration logic.
 */
@Component
public class PiperTextToSpeechProvider implements TextToSpeechProvider {
    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String defaultVoice;

    public PiperTextToSpeechProvider(
            @Value("${voice.tts.endpoint:http://127.0.0.1:8091}") String endpoint,
            @Value("${voice.tts.timeout-ms:7000}") int timeoutMs,
            @Value("${voice.tts.voice:en_US-lessac-medium}") String defaultVoice,
            RestTemplateBuilder builder
    ) {
        this.endpoint = endpoint;
        this.defaultVoice = defaultVoice;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public String providerName() {
        return "piper";
    }

    @Override
    public boolean isReady() {
        try {
            ResponseEntity<String> res = restTemplate.getForEntity(endpoint + "/v1/voice/health", String.class);
            return res.getStatusCode().is2xxSuccessful();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public SpeechSynthesisResult synthesize(SpeechSynthesisRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String voice = request.voiceName() == null || request.voiceName().isBlank() ? defaultVoice : request.voiceName();
            var payload = new TtsRequest(request.tenantId(), request.text(), voice, request.locale());
            ResponseEntity<TtsResponse> res = restTemplate.postForEntity(
                    endpoint + "/v1/tts/synthesize",
                    new HttpEntity<>(payload, headers),
                    TtsResponse.class
            );
            TtsResponse body = res.getBody();
            if (body == null || body.audioBase64() == null) {
                return new SpeechSynthesisResult(new byte[0], "audio/wav", 0L);
            }
            return new SpeechSynthesisResult(Base64.getDecoder().decode(body.audioBase64()), "audio/wav", body.durationMs() == null ? 0L : body.durationMs());
        } catch (Exception ex) {
            return new SpeechSynthesisResult(new byte[0], "audio/wav", 0L);
        }
    }

    private record TtsRequest(UUID tenantId, String text, String voice, String locale) {}
    private record TtsResponse(String audioBase64, Long durationMs) {}
}
