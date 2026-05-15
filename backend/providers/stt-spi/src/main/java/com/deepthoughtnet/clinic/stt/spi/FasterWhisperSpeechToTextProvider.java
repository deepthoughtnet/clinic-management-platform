package com.deepthoughtnet.clinic.stt.spi;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
 * HTTP adapter for faster-whisper runtime, keeping model execution outside the Java process.
 */
@Component
public class FasterWhisperSpeechToTextProvider implements SpeechToTextProvider {
    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String model;
    private final String defaultLanguage;
    private final String device;

    public FasterWhisperSpeechToTextProvider(
            @Value("${voice.stt.endpoint:http://127.0.0.1:8091}") String endpoint,
            @Value("${voice.stt.timeout-ms:7000}") int timeoutMs,
            @Value("${voice.stt.model:base}") String model,
            @Value("${voice.stt.language:en}") String defaultLanguage,
            @Value("${voice.stt.device:cpu}") String device,
            RestTemplateBuilder builder
    ) {
        this.endpoint = endpoint;
        this.model = model;
        this.defaultLanguage = defaultLanguage;
        this.device = device;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public String providerName() {
        return "faster-whisper";
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
    public StreamingSpeechSession openSession(UUID tenantId, String locale) {
        return new HttpStreamingSpeechSession(tenantId, locale == null || locale.isBlank() ? defaultLanguage : locale);
    }

    private final class HttpStreamingSpeechSession implements StreamingSpeechSession {
        private final UUID sessionId = UUID.randomUUID();
        private final UUID tenantId;
        private final String locale;
        private final List<TranscriptChunk> chunks = new ArrayList<>();

        private HttpStreamingSpeechSession(UUID tenantId, String locale) {
            this.tenantId = tenantId;
            this.locale = locale;
        }

        @Override
        public TranscriptChunk pushAudio(byte[] pcmChunk) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                var payload = new SttChunkRequest(
                        tenantId,
                        sessionId,
                        Base64.getEncoder().encodeToString(pcmChunk),
                        locale,
                        model,
                        device,
                        false
                );
                ResponseEntity<SttChunkResponse> res = restTemplate.postForEntity(
                        endpoint + "/v1/stt/chunk",
                        new HttpEntity<>(payload, headers),
                        SttChunkResponse.class
                );
                SttChunkResponse body = res.getBody();
                TranscriptChunk chunk = new TranscriptChunk(
                        body == null || body.text() == null ? "" : body.text(),
                        body != null && body.finalChunk(),
                        body == null ? null : body.confidence(),
                        OffsetDateTime.now()
                );
                chunks.add(chunk);
                return chunk;
            } catch (Exception ex) {
                TranscriptChunk fallback = new TranscriptChunk("", false, null, OffsetDateTime.now());
                chunks.add(fallback);
                return fallback;
            }
        }

        @Override
        public SpeechRecognitionResult finish() {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                var payload = new SttFinalizeRequest(tenantId, sessionId, locale, model, device);
                ResponseEntity<SttFinalizeResponse> res = restTemplate.postForEntity(
                        endpoint + "/v1/stt/finalize",
                        new HttpEntity<>(payload, headers),
                        SttFinalizeResponse.class
                );
                SttFinalizeResponse body = res.getBody();
                return new SpeechRecognitionResult(
                        body == null || body.text() == null ? "" : body.text(),
                        body == null ? null : body.confidence(),
                        List.copyOf(chunks)
                );
            } catch (Exception ex) {
                return new SpeechRecognitionResult("", null, List.copyOf(chunks));
            }
        }

        @Override
        public void close() {
        }
    }

    private record SttChunkRequest(UUID tenantId, UUID sessionId, String audioBase64, String language,
                                   String model, String device, boolean finalChunk) {}
    private record SttChunkResponse(String text, Double confidence, boolean finalChunk) {}
    private record SttFinalizeRequest(UUID tenantId, UUID sessionId, String language, String model, String device) {}
    private record SttFinalizeResponse(String text, Double confidence) {}
}
