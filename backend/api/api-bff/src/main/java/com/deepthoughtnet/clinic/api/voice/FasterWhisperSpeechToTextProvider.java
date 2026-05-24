package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.api.voice.spi.SpeechToTextProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component("voiceTestFasterWhisperSpeechToTextProvider")
public class FasterWhisperSpeechToTextProvider implements SpeechToTextProvider {
    private final VoiceTestProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FasterWhisperSpeechToTextProvider(VoiceTestProperties properties,
                                             RestTemplateBuilder builder,
                                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getStt().getFasterWhisper().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getStt().getFasterWhisper().getReadTimeoutMs()))
                .build();
    }

    @Override
    public String providerName() {
        return "faster-whisper";
    }

    @Override
    public boolean isReady() {
        return StringUtils.hasText(properties.getStt().getFasterWhisper().getBaseUrl());
    }

    @Override
    public VoiceTranscriptionResult transcribe(VoiceTranscriptionRequest request) {
        if (!isReady()) {
            throw new IllegalStateException("Local STT service unavailable");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", audioPart(request));
            body.add("language", StringUtils.hasText(request.language())
                    ? request.language()
                    : properties.getStt().getFasterWhisper().getLanguage());
            body.add("model", properties.getStt().getFasterWhisper().getModel());

            String baseUrl = properties.getStt().getFasterWhisper().getBaseUrl().replaceAll("/+$", "");
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/transcribe",
                    new HttpEntity<>(body, headers),
                    String.class
            );
            JsonNode root = objectMapper.readTree(response.getBody());
            String transcript = root.path("text").asText("");
            if (!StringUtils.hasText(transcript)) {
                throw new IllegalStateException("Audio could not be transcribed");
            }
            return new VoiceTranscriptionResult(transcript.trim(), providerName(), null);
        } catch (HttpStatusCodeException ex) {
            throw new IllegalStateException(extractErrorMessage(ex), ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Local STT service unavailable", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Audio could not be transcribed", ex);
        }
    }

    public VoiceServiceStatus status(boolean warmup) {
        if (!isReady()) {
            return new VoiceServiceStatus("FASTER_WHISPER", false, false, "Local STT service unavailable");
        }
        try {
            String baseUrl = properties.getStt().getFasterWhisper().getBaseUrl().replaceAll("/+$", "");
            String endpoint = baseUrl + (warmup ? "/ready" : "/health");
            ResponseEntity<Map> response = restTemplate.getForEntity(endpoint, Map.class);
            Map<?, ?> body = response.getBody();
            boolean ready = warmup
                    ? booleanValue(body, "modelLoaded")
                    : response.getStatusCode().is2xxSuccessful();
            String message = stringValue(body, "message");
            if (!StringUtils.hasText(message)) {
                message = warmup ? "Faster-Whisper model ready." : "Faster-Whisper reachable.";
            }
            return new VoiceServiceStatus("FASTER_WHISPER", true, ready, message);
        } catch (RestClientException ex) {
            return new VoiceServiceStatus("FASTER_WHISPER", false, false, "Local STT service unavailable");
        }
    }

    private HttpEntity<ByteArrayResource> audioPart(VoiceTranscriptionRequest request) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(parseContentType(request.contentType()));
        ByteArrayResource resource = new ByteArrayResource(request.audioBytes()) {
            @Override
            public String getFilename() {
                return StringUtils.hasText(request.filename()) ? request.filename() : "voice-test.webm";
            }
        };
        return new HttpEntity<>(resource, partHeaders);
    }

    private MediaType parseContentType(String value) {
        if (!StringUtils.hasText(value)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private boolean booleanValue(Map<?, ?> body, String key) {
        if (body == null) {
            return false;
        }
        Object value = body.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private String stringValue(Map<?, ?> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String extractErrorMessage(HttpStatusCodeException ex) {
        String body = ex.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return "Local STT service unavailable";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String detail = root.path("detail").asText("");
            if (StringUtils.hasText(detail)) {
                return detail;
            }
            String error = root.path("error").asText("");
            if (StringUtils.hasText(error)) {
                return error;
            }
        } catch (Exception ignored) {
        }
        return body;
    }
}
