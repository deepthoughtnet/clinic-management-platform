package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.api.voice.spi.TextToSpeechProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
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
    private static final Logger log = LoggerFactory.getLogger(PiperTextToSpeechProvider.class);
    private final VoiceTestProperties properties;
    private final RestTemplate restTemplate;
    private final Environment environment;

    @Autowired
    public PiperTextToSpeechProvider(VoiceTestProperties properties, RestTemplateBuilder builder) {
        this(properties, builder, new StandardEnvironment());
    }

    PiperTextToSpeechProvider(VoiceTestProperties properties, RestTemplateBuilder builder, Environment environment) {
        this.properties = properties;
        this.environment = environment;
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
        String normalizedLanguage = normalizeLanguage(request.language());
        String displayLanguage = "hi".equals(normalizedLanguage) ? "hi-IN"
                : "en".equals(normalizedLanguage) ? "en-IN" : normalizedLanguage;
        ResolvedVoiceSelection voiceSelection = resolveVoice(request.language());
        if (!StringUtils.hasText(voiceSelection.voice())) {
            throw new IllegalStateException("Hindi Piper voice is not configured.");
        }
        try {
            log.info(
                    "VOICE_PROVIDER_TRACE provider=piper defaultVoice={} fallbackVoice={} language={}",
                    resolveHindiPrimaryVoice(),
                    resolveHindiFallbackVoice(),
                    displayLanguage
            );
            log.info(
                    "voice.tts.request provider=PIPER language={} selectedVoice={} fallbackReason={}",
                    displayLanguage,
                    voiceSelection.voice(),
                    voiceSelection.fallbackReason()
            );
            return synthesizeWithVoice(request.text(), voiceSelection.voice());
        } catch (Exception ex) {
            String fallbackVoice = resolveHindiFallbackVoice();
            if ("hi".equals(normalizedLanguage)
                    && properties.getTts().getPiper().isAllowFallbackVoice()
                    && StringUtils.hasText(fallbackVoice)
                    && !fallbackVoice.equals(voiceSelection.voice())) {
                log.warn(
                        "voice.tts.fallback provider=PIPER language=hi fromVoice={} toVoice={} reason={}",
                        voiceSelection.voice(),
                        fallbackVoice,
                        ex.getMessage()
                );
                try {
                    return synthesizeWithVoice(request.text(), fallbackVoice);
                } catch (Exception fallbackEx) {
                    log.warn(
                            "voice.tts.fallback provider=PIPER language=hi fromVoice={} toVoice={} reason={}",
                            fallbackVoice,
                            resolveVoice("en").voice(),
                            fallbackEx.getMessage()
                    );
                }
            }
            String englishFallback = resolveVoice("en").voice();
            if (properties.getTts().getPiper().isAllowFallbackVoice()
                    && StringUtils.hasText(englishFallback)
                    && !englishFallback.equals(voiceSelection.voice())) {
                return synthesizeWithVoice(request.text(), englishFallback);
            }
            throw new IllegalStateException("Audio could not be synthesized", ex);
        }
    }

    public VoiceServiceStatus status(boolean warmup) {
        if (!isReady()) {
            return new VoiceServiceStatus("PIPER", false, false, "Local TTS service unavailable");
        }
        ResolvedVoiceSelection englishVoice = resolveVoice("en");
        ResolvedVoiceSelection hindiVoice = resolveVoice("hi");
        VoiceServiceStatus defaultStatus = statusForVoice(warmup, voiceSelectionOrDefault(englishVoice));
        StringBuilder message = new StringBuilder(defaultStatus.message());
        message.append(" English voice: ").append(voiceSelectionOrDefault(englishVoice)).append('.');
        if (StringUtils.hasText(properties.getTts().getPiper().getHindiVoice())) {
            VoiceServiceStatus hindiStatus = statusForVoice(warmup, properties.getTts().getPiper().getHindiVoice());
            message.append(" Hindi voice: ").append(properties.getTts().getPiper().getHindiVoice()).append(" (")
                    .append(hindiStatus.ready() ? "ready" : "warming")
                    .append(").");
        } else if (StringUtils.hasText(hindiVoice.fallbackReason())) {
            message.append(' ').append(hindiVoice.fallbackReason());
        }
        return new VoiceServiceStatus("PIPER", defaultStatus.reachable(), defaultStatus.ready(), message.toString().trim());
    }

    public String configuredVoiceForLanguage(String language) {
        return resolveVoice(language).voice();
    }

    public Map<String, String> configuredVoices() {
        Map<String, String> voices = new LinkedHashMap<>();
        if (StringUtils.hasText(configuredDefaultVoice())) {
            voices.put("default", configuredDefaultVoice());
        }
        String englishVoice = resolveVoice("en").voice();
        if (StringUtils.hasText(englishVoice)) {
            voices.put("en", englishVoice);
        }
        if (StringUtils.hasText(properties.getTts().getPiper().getHindiVoice())) {
            voices.put("hi", properties.getTts().getPiper().getHindiVoice());
        }
        return voices;
    }

    public boolean isLanguageVoiceConfigured(String language) {
        return resolveVoice(language).exactMatch();
    }

    public boolean isFallbackVoiceAllowed() {
        return properties.getTts().getPiper().isAllowFallbackVoice();
    }

    private VoiceServiceStatus statusForVoice(boolean warmup, String voice) {
        if (!StringUtils.hasText(voice)) {
            return new VoiceServiceStatus("PIPER", true, false, "Piper voice is not configured.");
        }
        try {
            String baseUrl = properties.getTts().getPiper().getBaseUrl().replaceAll("/+$", "");
            String endpoint = baseUrl + (warmup ? "/ready" : "/health");
            if (StringUtils.hasText(voice)) {
                endpoint = endpoint + "?voice=" + URLEncoder.encode(voice, StandardCharsets.UTF_8);
            }
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

    private String configuredDefaultVoice() {
        if (StringUtils.hasText(properties.getTts().getPiper().getVoice())) {
            return properties.getTts().getPiper().getVoice();
        }
        return properties.getTts().getPiper().getEnglishVoice();
    }

    private String resolveHindiPrimaryVoice() {
        String voice = env("PIPER_DEFAULT_VOICE");
        if (StringUtils.hasText(voice)) {
            return voice.trim();
        }
        if (StringUtils.hasText(properties.getTts().getPiper().getHindiVoice())) {
            return properties.getTts().getPiper().getHindiVoice().trim();
        }
        return null;
    }

    private String resolveHindiFallbackVoice() {
        String voice = env("PIPER_FALLBACK_VOICE");
        if (StringUtils.hasText(voice)) {
            return voice.trim();
        }
        return null;
    }

    private String voiceSelectionOrDefault(ResolvedVoiceSelection selection) {
        return StringUtils.hasText(selection.voice()) ? selection.voice() : configuredDefaultVoice();
    }

    private ResolvedVoiceSelection resolveVoice(String language) {
        String normalizedLanguage = normalizeLanguage(language);
        String defaultVoice = configuredDefaultVoice();
        String englishVoice = StringUtils.hasText(properties.getTts().getPiper().getEnglishVoice())
                ? properties.getTts().getPiper().getEnglishVoice()
                : defaultVoice;
        String hindiVoice = resolveHindiPrimaryVoice();
        String hindiFallbackVoice = resolveHindiFallbackVoice();
        boolean allowFallback = properties.getTts().getPiper().isAllowFallbackVoice();

        if ("hi".equals(normalizedLanguage)) {
            if (StringUtils.hasText(hindiVoice)) {
                return new ResolvedVoiceSelection("hi", hindiVoice, true, null);
            }
            if (StringUtils.hasText(hindiFallbackVoice)) {
                return new ResolvedVoiceSelection("hi", hindiFallbackVoice, false, "Hindi Piper voice is not configured. Using fallback voice.");
            }
            if (allowFallback && StringUtils.hasText(englishVoice)) {
                return new ResolvedVoiceSelection("hi", englishVoice, false, "Hindi Piper voice is not configured. Using fallback voice.");
            }
            return new ResolvedVoiceSelection("hi", null, false, "Hindi Piper voice is not configured.");
        }
        if ("en".equals(normalizedLanguage)) {
            return new ResolvedVoiceSelection("en", englishVoice, true, null);
        }
        if (StringUtils.hasText(defaultVoice)) {
            return new ResolvedVoiceSelection(normalizedLanguage, defaultVoice, true, null);
        }
        return new ResolvedVoiceSelection(normalizedLanguage, englishVoice, StringUtils.hasText(englishVoice), null);
    }

    private String env(String key) {
        return environment == null ? null : environment.getProperty(key);
    }

    private String normalizeLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            return "auto";
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("hi")) {
            return "hi";
        }
        if (normalized.startsWith("en")) {
            return "en";
        }
        return normalized;
    }

    private VoiceSynthesisResult synthesizeWithVoice(String text, String voice) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.parseMediaType("audio/wav")));

            Map<String, Object> payload = Map.of("text", text, "voice", voice);
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

    private record ResolvedVoiceSelection(
            String language,
            String voice,
            boolean exactMatch,
            String fallbackReason
    ) {
    }
}
