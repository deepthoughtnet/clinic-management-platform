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
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(120))
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
}
