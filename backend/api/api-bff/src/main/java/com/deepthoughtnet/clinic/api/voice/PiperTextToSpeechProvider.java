package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.api.voice.spi.TextToSpeechProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component("voiceTestPiperTextToSpeechProvider")
public class PiperTextToSpeechProvider implements TextToSpeechProvider {
    private final VoiceTestProperties properties;
    private final RestTemplate restTemplate;

    public PiperTextToSpeechProvider(VoiceTestProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getTts().getPiper().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTts().getPiper().getReadTimeoutMs()))
                .build();
    }

    @Override
    public String providerName() {
        return "piper";
    }

    @Override
    public boolean isReady() {
        return StringUtils.hasText(properties.getTts().getPiper().getBaseUrl());
    }

    @Override
    public VoiceSynthesisResult synthesize(VoiceSynthesisRequest request) {
        if (!isReady()) {
            throw new IllegalStateException("Local TTS service unavailable");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.parseMediaType("audio/wav")));

            Map<String, Object> payload = Map.of(
                    "text", request.text(),
                    "voice", StringUtils.hasText(properties.getTts().getPiper().getVoice())
                            ? properties.getTts().getPiper().getVoice()
                            : request.language()
            );
            String baseUrl = properties.getTts().getPiper().getBaseUrl().replaceAll("/+$", "");
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    baseUrl + "/synthesize",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    byte[].class
            );
            byte[] audio = response.getBody();
            if (audio == null || audio.length == 0) {
                throw new IllegalStateException("Local TTS service unavailable");
            }
            return new VoiceSynthesisResult(audio, "audio/wav", providerName(), null);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Local TTS service unavailable", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Audio could not be synthesized", ex);
        }
    }

    public VoiceServiceStatus status(boolean warmup) {
        if (!isReady()) {
            return new VoiceServiceStatus("PIPER", false, false, "Local TTS service unavailable");
        }
        try {
            String baseUrl = properties.getTts().getPiper().getBaseUrl().replaceAll("/+$", "");
            String endpoint = baseUrl + (warmup ? "/ready" : "/health");
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    Map.class
            );
            Map<?, ?> body = response.getBody();
            boolean ready = warmup
                    ? booleanValue(body, "voiceLoaded")
                    : response.getStatusCode().is2xxSuccessful();
            String message = stringValue(body, "message");
            if (!StringUtils.hasText(message)) {
                message = warmup ? "Piper voice ready." : "Piper reachable.";
            }
            return new VoiceServiceStatus("PIPER", true, ready, message);
        } catch (RestClientException ex) {
            return new VoiceServiceStatus("PIPER", false, false, "Local TTS service unavailable");
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
}
