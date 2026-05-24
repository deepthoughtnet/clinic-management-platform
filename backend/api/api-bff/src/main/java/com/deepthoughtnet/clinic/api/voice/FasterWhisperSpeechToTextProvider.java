package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.api.voice.spi.SpeechToTextProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(120))
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
        } catch (RestClientException ex) {
            throw new IllegalStateException("Local STT service unavailable", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Audio could not be transcribed", ex);
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
}
