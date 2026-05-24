package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.api.voice.spi.SpeechToTextProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class DeepgramSpeechToTextProvider implements SpeechToTextProvider {
    private final VoiceTestProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DeepgramSpeechToTextProvider(VoiceTestProperties properties, RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(20))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }

    @Override
    public String providerName() {
        return "deepgram";
    }

    @Override
    public boolean isReady() {
        return StringUtils.hasText(properties.getStt().getDeepgram().getApiKey());
    }

    @Override
    public VoiceTranscriptionResult transcribe(VoiceTranscriptionRequest request) {
        if (!isReady()) {
            throw new IllegalStateException("Deepgram STT is not configured.");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(properties.getStt().getDeepgram().getApiKey().trim());
            headers.setContentType(parseContentType(request.contentType()));

            String url = UriComponentsBuilder.fromHttpUrl(properties.getStt().getDeepgram().getBaseUrl())
                    .queryParam("model", properties.getStt().getDeepgram().getModel())
                    .queryParam("smart_format", true)
                    .queryParamIfPresent("language", StringUtils.hasText(request.language()) ? java.util.Optional.of(request.language()) : java.util.Optional.empty())
                    .build(true)
                    .toUriString();

            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(request.audioBytes(), headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode alternative = root.path("results").path("channels").path(0).path("alternatives").path(0);
            String transcript = alternative.path("transcript").asText("");
            if (!StringUtils.hasText(transcript)) {
                throw new IllegalStateException("Deepgram returned an empty transcript.");
            }
            return new VoiceTranscriptionResult(transcript, providerName(), null);
        } catch (Exception ex) {
            throw new IllegalStateException("Deepgram transcription failed: " + ex.getMessage(), ex);
        }
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
}

