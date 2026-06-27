package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.api.voice.spi.SpeechToTextProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.util.UriComponentsBuilder;

@Component("voiceTestSarvamSpeechToTextProvider")
public class SarvamSpeechToTextProvider implements SpeechToTextProvider {
    private static final Logger log = LoggerFactory.getLogger(SarvamSpeechToTextProvider.class);
    private static final String CONFIGURED_PROVIDER_KEY = "sarvam";
    private static final String TRACE_PROVIDER_NAME = "sarvam";

    private final VoiceTestProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SarvamSpeechToTextProvider(VoiceTestProperties properties,
                                      RestTemplateBuilder builder,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getSarvam().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getSarvam().getReadTimeoutMs()))
                .build();
        log.info("VOICE_PROVIDER_TRACE provider=sarvam type=STT enabled={} sttEnabled={} apiKeyConfigured={} baseUrl={} sttPath={} defaultLanguage={} responseLanguage={} connectTimeoutMs={} readTimeoutMs={}",
                properties.getSarvam().isEnabled(),
                properties.getSarvam().isSttEnabled(),
                StringUtils.hasText(properties.getSarvam().getApiKey()),
                sanitizedBaseUrl(),
                properties.getSarvam().getSttPath(),
                properties.getDefaultLanguage(),
                properties.getResponseLanguage(),
                properties.getSarvam().getConnectTimeoutMs(),
                properties.getSarvam().getReadTimeoutMs());
    }

    @Override
    public String providerName() {
        return CONFIGURED_PROVIDER_KEY;
    }

    @Override
    public boolean isReady() {
        return properties.getSarvam().isEnabled()
                && properties.getSarvam().isSttEnabled()
                && StringUtils.hasText(properties.getSarvam().getApiKey())
                && StringUtils.hasText(properties.getSarvam().getBaseUrl())
                && StringUtils.hasText(properties.getSarvam().getSttPath());
    }

    @Override
    public VoiceTranscriptionResult transcribe(VoiceTranscriptionRequest request) {
        if (!isReady()) {
            throw new IllegalStateException("Sarvam STT is not configured.");
        }
        String language = resolveLanguage(request.language());
        String endpoint = endpointUrl(properties.getSarvam().getSttPath());
        String originalContentType = request.contentType();
        String sarvamContentType = normalizeSarvamContentType(originalContentType);
        Instant started = Instant.now();
        log.info("voice.stt.request provider=sarvam endpoint={} filename={} originalContentType={} sarvamContentType={} sizeBytes={} language={} connectTimeoutMs={} readTimeoutMs={}",
                endpoint,
                safeFilename(request.filename()),
                originalContentType,
                sarvamContentType,
                request.audioBytes() == null ? 0 : request.audioBytes().length,
                language,
                properties.getSarvam().getConnectTimeoutMs(),
                properties.getSarvam().getReadTimeoutMs());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, new MediaType("audio", "*")));
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            String apiKey = properties.getSarvam().getApiKey().trim();
            headers.setBearerAuth(apiKey);
            headers.set("x-api-key", apiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", audioPart(request));
            body.add("language", language);

            String url = UriComponentsBuilder.fromHttpUrl(endpoint)
                    .queryParamIfPresent("language", StringUtils.hasText(language)
                            ? java.util.Optional.of(language)
                            : java.util.Optional.empty())
                    .build(true)
                    .toUriString();

            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            String transcript = extractTranscript(response.getBody());
            if (!StringUtils.hasText(transcript)) {
                throw new IllegalStateException("Sarvam returned an empty transcript.");
            }
            String trimmed = transcript.trim();
            log.info("voice.stt.complete provider=sarvam latencyMs={} transcriptChars={}",
                    Duration.between(started, Instant.now()).toMillis(),
                    trimmed.length());
            return new VoiceTranscriptionResult(trimmed, providerName(), null);
        } catch (HttpStatusCodeException ex) {
            String bodyPreview = summarize(ex.getResponseBodyAsString());
            log.warn("voice.stt.failed provider=sarvam status={} endpoint={} contentType={} sizeBytes={} body={} reason={}",
                    ex.getStatusCode().value(),
                    endpoint,
                    request.contentType(),
                    request.audioBytes() == null ? 0 : request.audioBytes().length,
                    bodyPreview,
                    ex.getMessage());
            throw new IllegalStateException("Sarvam transcription failed: " + extractReason(ex), ex);
        } catch (RestClientException ex) {
            log.warn("voice.stt.failed provider=sarvam endpoint={} contentType={} sizeBytes={} exception={} reason={}",
                    endpoint,
                    request.contentType(),
                    request.audioBytes() == null ? 0 : request.audioBytes().length,
                    ex.getClass().getSimpleName(),
                    rootCauseMessage(ex));
            throw new IllegalStateException("Sarvam transcription failed: " + rootCauseMessage(ex), ex);
        } catch (Exception ex) {
            log.warn("voice.stt.failed provider=sarvam endpoint={} exception={} reason={}",
                    endpoint,
                    ex.getClass().getSimpleName(),
                    rootCauseMessage(ex));
            throw new IllegalStateException("Sarvam transcription failed: " + rootCauseMessage(ex), ex);
        }
    }

    private HttpEntity<ByteArrayResource> audioPart(VoiceTranscriptionRequest request) {
        byte[] audioBytes = request.audioBytes() == null ? new byte[0] : request.audioBytes();
        String filename = safeFilename(request.filename());
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(parseContentType(normalizeSarvamContentType(request.contentType())));
        return new HttpEntity<>(new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, partHeaders);
    }

    private String normalizeSarvamContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "audio/webm";
        }
        String normalized = contentType.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("audio/webm")) {
            return "audio/webm";
        }
        if (lower.startsWith("audio/opus")) {
            return "audio/opus";
        }
        if (lower.startsWith("video/webm")) {
            return "video/webm";
        }
        return normalized;
    }

    private String resolveLanguage(String requestLanguage) {
        if (StringUtils.hasText(requestLanguage)) {
            String normalized = requestLanguage.trim();
            if (!"auto".equalsIgnoreCase(normalized) && !"auto-detect".equalsIgnoreCase(normalized)) {
                if ("hi".equalsIgnoreCase(normalized) || "hi-in".equalsIgnoreCase(normalized)) {
                    return "hi-IN";
                }
                return normalized;
            }
        }
        if (StringUtils.hasText(properties.getDefaultLanguage())) {
            return properties.getDefaultLanguage().trim();
        }
        if (StringUtils.hasText(properties.getSarvam().getDefaultLanguage())) {
            return properties.getSarvam().getDefaultLanguage().trim();
        }
        return "hi-IN";
    }

    private String extractTranscript(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return trimmed;
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            String transcript = firstNonBlank(
                    text(root, "text"),
                    text(root, "transcript"),
                    text(root, "transcription"),
                    text(root, "outputText"),
                    text(root.path("data"), "text"),
                    text(root.path("data"), "transcript"),
                    text(root.path("result"), "text")
            );
            if (StringUtils.hasText(transcript)) {
                return transcript;
            }
        } catch (Exception ex) {
            // fall through to plain-text interpretation below
        }
        return trimmed;
    }

    private String extractReason(HttpStatusCodeException ex) {
        String body = ex.getResponseBodyAsString();
        if (StringUtils.hasText(body)) {
            return summarize(body);
        }
        return ex.getStatusText();
    }

    private String endpointUrl(String path) {
        String baseUrl = properties.getSarvam().getBaseUrl().replaceAll("/+$", "");
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return baseUrl + normalizedPath;
    }

    private String sanitizedBaseUrl() {
        return StringUtils.hasText(properties.getSarvam().getBaseUrl())
                ? properties.getSarvam().getBaseUrl().replaceAll("/+$", "")
                : "";
    }

    private String safeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "audio.webm";
        }
        return originalFilename.replaceAll("[\\r\\n]", "_");
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

    private String summarize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240) + "...";
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return StringUtils.hasText(message) ? message : throwable.getClass().getSimpleName();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field) || !node.get(field).isValueNode()) {
            return null;
        }
        String value = node.get(field).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }
}
