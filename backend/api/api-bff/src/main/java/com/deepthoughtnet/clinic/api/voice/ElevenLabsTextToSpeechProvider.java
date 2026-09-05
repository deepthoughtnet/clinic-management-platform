package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.api.voice.spi.TextToSpeechProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class ElevenLabsTextToSpeechProvider implements TextToSpeechProvider {
    private static final Logger log = LoggerFactory.getLogger(ElevenLabsTextToSpeechProvider.class);
    static final String SAFE_FAILURE_MESSAGE = "ElevenLabs request failed";
    private static final String AUDIO_CONTENT_TYPE_PREFIX = "audio/";
    private static final String OUTPUT_FORMAT = "mp3_44100_128";

    private final VoiceTestProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public ElevenLabsTextToSpeechProvider(VoiceTestProperties properties, ObjectMapper objectMapper, RestTemplateBuilder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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
                && StringUtils.hasText(properties.getTts().getElevenlabs().getVoiceId())
                && StringUtils.hasText(properties.getTts().getElevenlabs().getModel())
                && StringUtils.hasText(properties.getTts().getElevenlabs().getBaseUrl());
    }

    @Override
    public VoiceSynthesisResult synthesize(VoiceSynthesisRequest request) {
        if (!isReady()) {
            throw new IllegalStateException("ElevenLabs TTS is not configured.");
        }
        if (request == null || !StringUtils.hasText(request.text())) {
            throw new IllegalArgumentException("ElevenLabs text is required.");
        }
        Instant started = Instant.now();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("xi-api-key", properties.getTts().getElevenlabs().getApiKey().trim());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.parseMediaType("audio/mpeg")));

            String url = properties.getTts().getElevenlabs().getBaseUrl().replaceAll("/+$", "")
                    + "/" + properties.getTts().getElevenlabs().getVoiceId().trim();
            Map<String, Object> payload = Map.of(
                    "text", request.text(),
                    "model_id", properties.getTts().getElevenlabs().getModel(),
                    "output_format", OUTPUT_FORMAT
            );
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    byte[].class
            );
            byte[] audio = response.getBody();
            long latencyMs = Duration.between(started, Instant.now()).toMillis();
            String contentType = response.getHeaders().getContentType() == null ? null : response.getHeaders().getContentType().toString();
            int byteLength = audio == null ? 0 : audio.length;
            if (!response.getStatusCode().is2xxSuccessful()) {
                String category = categorizeStatus(response.getStatusCodeValue(), contentType, byteLength, audio);
                log.warn("voice.tts.elevenlabs.failed status={} category={}",
                        response.getStatusCodeValue(),
                        category);
                throw new ElevenLabsTtsException(
                        category,
                        safeFailureMessage(category),
                        response.getStatusCodeValue(),
                        contentType,
                        byteLength,
                        latencyMs,
                        null,
                        extractSafeDetailStatus(audio)
                );
            }
            if (!isAudioCompatible(contentType)) {
                String detailStatus = extractSafeDetailStatus(audio);
                String category = categorizeErrorStatus(response.getStatusCodeValue(), detailStatus, contentType, byteLength);
                log.warn("voice.tts.elevenlabs.failed status={} category={}",
                        response.getStatusCodeValue(),
                        category);
                throw new ElevenLabsTtsException(
                        category,
                        safeFailureMessage(category),
                        response.getStatusCodeValue(),
                        contentType,
                        byteLength,
                        latencyMs,
                        null,
                        detailStatus
                );
            }
            if (byteLength == 0) {
                log.warn("voice.tts.elevenlabs.failed status={} category={}",
                        response.getStatusCodeValue(),
                        "EMPTY_AUDIO_RESPONSE");
                throw new ElevenLabsTtsException(
                        "EMPTY_AUDIO_RESPONSE",
                        safeFailureMessage("EMPTY_AUDIO_RESPONSE"),
                        response.getStatusCodeValue(),
                        contentType,
                        byteLength,
                        latencyMs,
                        null,
                        null
                );
            }
            log.info("voice.tts.elevenlabs.success status={} contentType={} bytes={} latencyMs={}",
                    response.getStatusCodeValue(),
                    contentType,
                    byteLength,
                    latencyMs);
            return new VoiceSynthesisResult(audio, contentType, providerName(), null);
        } catch (ElevenLabsTtsException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            long latencyMs = Duration.between(started, Instant.now()).toMillis();
            String contentType = ex.getResponseHeaders() == null || ex.getResponseHeaders().getContentType() == null
                    ? null
                    : ex.getResponseHeaders().getContentType().toString();
            int byteLength = ex.getResponseBodyAsByteArray() == null ? 0 : ex.getResponseBodyAsByteArray().length;
            String detailStatus = extractSafeDetailStatus(ex.getResponseBodyAsByteArray());
            String category = categorizeErrorStatus(ex.getRawStatusCode(), detailStatus, contentType, byteLength);
            log.warn("voice.tts.elevenlabs.failed status={} category={}",
                    ex.getRawStatusCode(),
                    category);
            throw new ElevenLabsTtsException(
                    category,
                    safeFailureMessage(category),
                    ex.getRawStatusCode(),
                    contentType,
                    byteLength,
                    latencyMs,
                    ex,
                    detailStatus
            );
        } catch (RestClientException ex) {
            long latencyMs = Duration.between(started, Instant.now()).toMillis();
            String category = categorizeTransport(ex);
            log.warn("voice.tts.elevenlabs.failed status={} category={}",
                    null,
                    category);
            throw new ElevenLabsTtsException(
                    category,
                    safeFailureMessage(category),
                    null,
                    null,
                    0,
                    latencyMs,
                    ex,
                    null
            );
        } catch (RuntimeException ex) {
            long latencyMs = Duration.between(started, Instant.now()).toMillis();
            log.warn("voice.tts.elevenlabs.failed status={} category={}",
                    null,
                    "UNKNOWN");
            throw new ElevenLabsTtsException(
                    "UNKNOWN",
                    safeFailureMessage("UNKNOWN"),
                    null,
                    null,
                    0,
                    latencyMs,
                    ex,
                    null
            );
        }
    }

    private boolean isAudioCompatible(String contentType) {
        return StringUtils.hasText(contentType) && contentType.toLowerCase(java.util.Locale.ROOT).startsWith(AUDIO_CONTENT_TYPE_PREFIX);
    }

    private String safeFailureMessage(String category) {
        return switch (category == null ? "UNKNOWN" : category) {
            case "AUTHENTICATION_FAILED", "AUTHENTICATION" -> "Authentication failed";
            case "VOICE_NOT_FOUND" -> "Voice ID not found or not accessible";
            case "QUOTA" -> "ElevenLabs quota exceeded";
            case "INVALID_REQUEST" -> "Invalid model or request";
            case "RATE_LIMIT" -> "Rate limit reached";
            default -> SAFE_FAILURE_MESSAGE;
        };
    }

    private String categorizeStatus(int statusCode, String contentType, int byteLength, byte[] body) {
        if (statusCode == 401 || statusCode == 403) {
            return "AUTHENTICATION_FAILED";
        }
        if (statusCode == 402 || statusCode == 429) {
            return "QUOTA";
        }
        if (statusCode == 404 || statusCode == 400 || statusCode == 422) {
            return "INVALID_VOICE_OR_MODEL";
        }
        if (statusCode == 408 || statusCode == 504) {
            return "TIMEOUT";
        }
        if (statusCode >= 500 && statusCode <= 599) {
            return "UNAVAILABLE";
        }
        if (!isAudioCompatible(contentType)) {
            return categorizeErrorStatus(statusCode, extractSafeDetailStatus(body), contentType, byteLength);
        }
        if (byteLength == 0) {
            return "EMPTY_AUDIO_RESPONSE";
        }
        return "UNKNOWN";
    }

    private String categorizeErrorStatus(int statusCode, String detailStatus, String contentType, int byteLength) {
        String normalizedDetailStatus = normalizeDetailStatus(detailStatus);
        if ("invalid_api_key".equals(normalizedDetailStatus) || "api_key_id_used_as_api_key".equals(normalizedDetailStatus)) {
            return "AUTHENTICATION_FAILED";
        }
        if ("voice_not_found".equals(normalizedDetailStatus)) {
            return "VOICE_NOT_FOUND";
        }
        if ("quota_exceeded".equals(normalizedDetailStatus)) {
            return "QUOTA";
        }
        if ("rate_limit".equals(normalizedDetailStatus) || "too_many_requests".equals(normalizedDetailStatus)) {
            return "RATE_LIMIT";
        }
        if (normalizedDetailStatus != null && normalizedDetailStatus.contains("validation")) {
            return "INVALID_REQUEST";
        }
        if (statusCode == 401 || statusCode == 403) {
            return "AUTHENTICATION";
        }
        if (statusCode == 402) {
            return "QUOTA";
        }
        if (statusCode == 404) {
            return "VOICE_NOT_FOUND";
        }
        if (statusCode == 429) {
            return "RATE_LIMIT";
        }
        if (statusCode == 400 || statusCode == 422) {
            return "INVALID_REQUEST";
        }
        if (!isAudioCompatible(contentType)) {
            return "UNKNOWN";
        }
        if (byteLength == 0) {
            return "EMPTY_AUDIO_RESPONSE";
        }
        return "UNKNOWN";
    }

    private String normalizeDetailStatus(String detailStatus) {
        if (!StringUtils.hasText(detailStatus)) {
            return null;
        }
        return detailStatus.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String extractSafeDetailStatus(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode detailNode = root.path("detail");
            if (detailNode.isMissingNode()) {
                return null;
            }
            JsonNode statusNode = detailNode.path("status");
            if (statusNode.isTextual() && StringUtils.hasText(statusNode.asText())) {
                return statusNode.asText();
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String categorizeTransport(RestClientException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof java.net.SocketTimeoutException) {
            return "TIMEOUT";
        }
        if (cause instanceof java.net.ConnectException) {
            return "UNAVAILABLE";
        }
        return "TRANSPORT";
    }

    static final class ElevenLabsTtsException extends IllegalStateException {
        private final String category;
        private final Integer statusCode;
        private final String contentType;
        private final int responseBytes;
        private final long latencyMs;
        private final String detailStatus;

        ElevenLabsTtsException(String category,
                               String message,
                               Integer statusCode,
                               String contentType,
                               int responseBytes,
                               long latencyMs,
                               Throwable cause,
                               String detailStatus) {
            super(message, cause);
            this.category = category;
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.responseBytes = responseBytes;
            this.latencyMs = latencyMs;
            this.detailStatus = detailStatus;
        }

        String category() {
            return category;
        }

        Integer statusCode() {
            return statusCode;
        }

        String contentType() {
            return contentType;
        }

        int responseBytes() {
            return responseBytes;
        }

        long latencyMs() {
            return latencyMs;
        }

        String detailStatus() {
            return detailStatus;
        }
    }
}
