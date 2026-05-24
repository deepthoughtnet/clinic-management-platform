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
import org.springframework.web.client.RestTemplate;

@Component
public class ElevenLabsTextToSpeechProvider implements TextToSpeechProvider {
    private final VoiceTestProperties properties;
    private final RestTemplate restTemplate;

    public ElevenLabsTextToSpeechProvider(VoiceTestProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(20))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }

    @Override
    public String providerName() {
        return "elevenlabs";
    }

    @Override
    public boolean isReady() {
        return StringUtils.hasText(properties.getTts().getElevenlabs().getApiKey())
                && StringUtils.hasText(properties.getTts().getElevenlabs().getVoiceId());
    }

    @Override
    public VoiceSynthesisResult synthesize(VoiceSynthesisRequest request) {
        if (!isReady()) {
            throw new IllegalStateException("ElevenLabs TTS is not configured.");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("xi-api-key", properties.getTts().getElevenlabs().getApiKey().trim());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.parseMediaType("audio/mpeg")));

            String url = properties.getTts().getElevenlabs().getBaseUrl().replaceAll("/+$", "")
                    + "/" + properties.getTts().getElevenlabs().getVoiceId().trim();
            Map<String, Object> payload = Map.of(
                    "text", request.text(),
                    "model_id", properties.getTts().getElevenlabs().getModel()
            );
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    byte[].class
            );
            byte[] audio = response.getBody();
            return new VoiceSynthesisResult(audio == null ? new byte[0] : audio, "audio/mpeg", providerName(), null);
        } catch (Exception ex) {
            throw new IllegalStateException("ElevenLabs synthesis failed: " + ex.getMessage(), ex);
        }
    }
}

