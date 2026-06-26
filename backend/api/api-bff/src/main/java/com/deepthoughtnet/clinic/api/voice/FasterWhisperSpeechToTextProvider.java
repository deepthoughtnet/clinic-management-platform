package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.api.voice.spi.SpeechToTextProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

@Component("voiceTestFasterWhisperSpeechToTextProvider")
public class FasterWhisperSpeechToTextProvider implements SpeechToTextProvider {
    private static final Logger log = LoggerFactory.getLogger(FasterWhisperSpeechToTextProvider.class);
    private static final String CONFIGURED_PROVIDER_KEY = "faster-whisper";
    private static final String TRACE_PROVIDER_NAME = "FASTER_WHISPER";
    private static final String HEALTH_PATH = "/health";
    private static final String READY_PATH = "/ready";
    private static final String TRANSCRIBE_PATH = "/transcribe";
    private static final int MAX_TRANSCRIBE_ATTEMPTS = 2;
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
        return CONFIGURED_PROVIDER_KEY;
    }

    @Override
    public boolean isReady() {
        return StringUtils.hasText(properties.getStt().getFasterWhisper().getBaseUrl());
    }

    @Override
    public VoiceTranscriptionResult transcribe(VoiceTranscriptionRequest request) {
        return transcribeWithDebug(request, null);
    }

    public VoiceTranscriptionResult transcribeWithDebug(VoiceTranscriptionRequest request, List<VoiceDebugTraceEntry> debugTrace) {
        if (!isReady()) {
            addTrace(debugTrace, new VoiceDebugTraceEntry(
                    "STT_PROVIDER_SELECTED", false, providerName(), null, null,
                    request.filename(), request.contentType(), sizeOf(request), null, null,
                    null, null, null, null, "Local STT service unavailable", null
            ));
            throw new IllegalStateException("Local STT service unavailable");
        }
        log.info("voice.stt.request provider={} baseUrl={} endpoint={} filename={} contentType={} sizeBytes={} connectTimeoutMs={} readTimeoutMs={}",
                providerName(),
                sanitizedBaseUrl(),
                transcribeUrl(),
                request.filename(),
                request.contentType(),
                request.audioBytes() == null ? 0 : request.audioBytes().length,
                properties.getStt().getFasterWhisper().getConnectTimeoutMs(),
                properties.getStt().getFasterWhisper().getReadTimeoutMs());
        String url = transcribeUrl();
        addTrace(debugTrace, new VoiceDebugTraceEntry(
                "STT_PROVIDER_SELECTED", true, providerName(), null, null,
                request.filename(), request.contentType(), sizeOf(request), null, null,
                null, null, null, null, null, null
        ));
        addTrace(debugTrace, new VoiceDebugTraceEntry(
                "FASTER_WHISPER_REQUEST", true, providerName(), null, null,
                request.filename(), request.contentType(), sizeOf(request), url, "file",
                null, null, null, null, null, null
        ));
        IllegalStateException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_TRANSCRIBE_ATTEMPTS; attempt += 1) {
            Instant started = Instant.now();
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(
                        url,
                        multipartRequest(request),
                        String.class
                );
                log.info("voice.stt.http.complete provider={} status={} attempt={} filename={} contentType={}",
                        TRACE_PROVIDER_NAME,
                        response.getStatusCode().value(),
                        attempt,
                        request.filename(),
                        request.contentType());
                addTrace(debugTrace, new VoiceDebugTraceEntry(
                        "FASTER_WHISPER_RESPONSE", response.getStatusCode().is2xxSuccessful(), providerName(), null, null,
                        request.filename(), request.contentType(), sizeOf(request), url, "file",
                        response.getStatusCode().value(), summarizeBody(response.getBody()),
                        Duration.between(started, Instant.now()).toMillis(), null, null, null
                ));
                JsonNode root = objectMapper.readTree(response.getBody());
                String transcript = root.path("text").asText("");
                if (!StringUtils.hasText(transcript)) {
                    throw new IllegalStateException("Audio could not be transcribed");
                }
                addTrace(debugTrace, new VoiceDebugTraceEntry(
                        "STT_RESULT", true, providerName(), null, null,
                        request.filename(), request.contentType(), sizeOf(request), null, null,
                        response.getStatusCode().value(), null,
                        Duration.between(started, Instant.now()).toMillis(), transcript.trim().length(), null, null
                ));
                return new VoiceTranscriptionResult(transcript.trim(), TRACE_PROVIDER_NAME, null);
            } catch (HttpStatusCodeException ex) {
                String reason = extractErrorMessage(ex);
                log.warn("voice.stt.http.failed provider={} status={} attempt={} baseUrl={} endpoint={} filename={} contentType={} body={}",
                        TRACE_PROVIDER_NAME,
                        ex.getStatusCode().value(),
                        attempt,
                        sanitizedBaseUrl(),
                        url,
                        request.filename(),
                        request.contentType(),
                        summarizeBody(ex.getResponseBodyAsString()));
                addTrace(debugTrace, new VoiceDebugTraceEntry(
                        "FASTER_WHISPER_RESPONSE", false, providerName(), null, null,
                        request.filename(), request.contentType(), sizeOf(request), url, "file",
                        ex.getStatusCode().value(), summarizeBody(ex.getResponseBodyAsString()),
                        Duration.between(started, Instant.now()).toMillis(), null, reason, null
                ));
                lastFailure = new IllegalStateException(reason, ex);
                if (ex.getStatusCode().is5xxServerError() && attempt < MAX_TRANSCRIBE_ATTEMPTS) {
                    log.warn("voice.stt.retry provider={} attempt={} nextAttempt={} reason={}",
                            TRACE_PROVIDER_NAME,
                            attempt,
                            attempt + 1,
                            reason);
                    continue;
                }
                break;
            } catch (RestClientException ex) {
                String reason = rootCauseMessage(ex);
                log.warn("voice.stt.transport.failed provider={} attempt={} baseUrl={} endpoint={} filename={} contentType={} exception={} reason={}",
                        TRACE_PROVIDER_NAME,
                        attempt,
                        sanitizedBaseUrl(),
                        url,
                        request.filename(),
                        request.contentType(),
                        ex.getClass().getSimpleName(),
                        reason);
                addTrace(debugTrace, new VoiceDebugTraceEntry(
                        "FASTER_WHISPER_RESPONSE", false, providerName(), null, null,
                        request.filename(), request.contentType(), sizeOf(request), url, "file",
                        null, null, Duration.between(started, Instant.now()).toMillis(), null, reason, null
                ));
                lastFailure = new IllegalStateException("Local STT service unavailable", ex);
                if (attempt < MAX_TRANSCRIBE_ATTEMPTS) {
                    log.warn("voice.stt.retry provider={} attempt={} nextAttempt={} reason={}",
                            TRACE_PROVIDER_NAME,
                            attempt,
                            attempt + 1,
                            reason);
                    continue;
                }
            } catch (Exception ex) {
                String reason = rootCauseMessage(ex);
                log.warn("voice.stt.fallback provider={} attempt={} reason={}", TRACE_PROVIDER_NAME, attempt, reason);
                addTrace(debugTrace, new VoiceDebugTraceEntry(
                        "FASTER_WHISPER_RESPONSE", false, providerName(), null, null,
                        request.filename(), request.contentType(), sizeOf(request), url, "file",
                        null, null, Duration.between(started, Instant.now()).toMillis(), null, reason, null
                ));
                lastFailure = new IllegalStateException("Audio could not be transcribed", ex);
                break;
            }
        }
        log.warn("voice.stt.fallback provider={} reason={}",
                TRACE_PROVIDER_NAME,
                lastFailure == null ? "Local STT service unavailable" : lastFailure.getMessage());
        throw lastFailure == null ? new IllegalStateException("Local STT service unavailable") : lastFailure;
    }

    public VoiceServiceStatus status(boolean warmup) {
        if (!isReady()) {
            return new VoiceServiceStatus("FASTER_WHISPER", false, false, "Local STT service unavailable");
        }
        try {
            String endpoint = statusUrl(warmup);
            log.info("voice.stt.health.request provider={} endpoint={} warmup={} connectTimeoutMs={} readTimeoutMs={}",
                    TRACE_PROVIDER_NAME,
                    endpoint,
                    warmup,
                    properties.getStt().getFasterWhisper().getConnectTimeoutMs(),
                    properties.getStt().getFasterWhisper().getReadTimeoutMs());
            ResponseEntity<Map> response = restTemplate.getForEntity(endpoint, Map.class);
            Map<?, ?> body = response.getBody();
            boolean ready = warmup
                    ? booleanValue(body, "modelLoaded")
                    : response.getStatusCode().is2xxSuccessful();
            String message = stringValue(body, "message");
            if (!StringUtils.hasText(message)) {
                message = warmup ? "Faster-Whisper model ready." : "Faster-Whisper reachable.";
            }
            log.info("voice.stt.health.complete provider={} endpoint={} warmup={} status={} ready={} body={}",
                    TRACE_PROVIDER_NAME,
                    endpoint,
                    warmup,
                    response.getStatusCode().value(),
                    ready,
                    summarizeBody(writeValue(body)));
            return new VoiceServiceStatus("FASTER_WHISPER", true, ready, message);
        } catch (RestClientException ex) {
            log.warn("voice.stt.health.failed provider={} endpoint={} warmup={} exception={} reason={}",
                    TRACE_PROVIDER_NAME,
                    statusUrl(warmup),
                    warmup,
                    ex.getClass().getSimpleName(),
                    rootCauseMessage(ex));
            return new VoiceServiceStatus("FASTER_WHISPER", false, false, "Local STT service unavailable");
        }
    }

    private HttpEntity<MultiValueMap<String, Object>> multipartRequest(VoiceTranscriptionRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", audioPart(request));
        if (StringUtils.hasText(request.language())
                && !"auto".equalsIgnoreCase(request.language().trim())
                && !"auto-detect".equalsIgnoreCase(request.language().trim())) {
            body.add("language", request.language().trim());
        }
        body.add("task", "transcribe");
        body.add("model", properties.getStt().getFasterWhisper().getModel());
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<ByteArrayResource> audioPart(VoiceTranscriptionRequest request) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(parseContentType(request.contentType()));
        ByteArrayResource resource = new ByteArrayResource(request.audioBytes()) {
            @Override
            public String getFilename() {
                return normalizedFilename(request.filename(), request.contentType());
            }
        };
        return new HttpEntity<>(resource, partHeaders);
    }

    private String normalizedFilename(String filename, String contentType) {
        if (!StringUtils.hasText(filename)) {
            return defaultFilenameForContentType(contentType);
        }
        String normalizedContentType = StringUtils.hasText(contentType)
                ? contentType.trim().toLowerCase(java.util.Locale.ROOT)
                : "";
        if (normalizedContentType.startsWith("audio/webm") && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".weba")) {
            return filename.substring(0, filename.length() - 5) + ".webm";
        }
        return filename;
    }

    private String defaultFilenameForContentType(String contentType) {
        String normalizedContentType = StringUtils.hasText(contentType)
                ? contentType.trim().toLowerCase(java.util.Locale.ROOT)
                : "";
        if (normalizedContentType.startsWith("audio/webm")) {
            return "voice-test.webm";
        }
        if (normalizedContentType.startsWith("audio/ogg")) {
            return "voice-test.ogg";
        }
        if (normalizedContentType.startsWith("audio/wav") || normalizedContentType.startsWith("audio/x-wav")) {
            return "voice-test.wav";
        }
        if (normalizedContentType.startsWith("audio/mp4") || normalizedContentType.startsWith("audio/x-m4a")) {
            return "voice-test.m4a";
        }
        if (normalizedContentType.startsWith("audio/mpeg") || normalizedContentType.startsWith("audio/mp3")) {
            return "voice-test.mp3";
        }
        return "voice-test.webm";
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

    private String summarizeBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "...";
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return StringUtils.hasText(message) ? message : current.getClass().getSimpleName();
    }

    private void addTrace(List<VoiceDebugTraceEntry> debugTrace, VoiceDebugTraceEntry entry) {
        if (debugTrace != null) {
            debugTrace.add(entry);
        }
    }

    private long sizeOf(VoiceTranscriptionRequest request) {
        return request.audioBytes() == null ? 0 : request.audioBytes().length;
    }

    private String sanitizedBaseUrl() {
        return properties.getStt().getFasterWhisper().getBaseUrl().replaceAll("/+$", "");
    }

    private String transcribeUrl() {
        return sanitizedBaseUrl() + TRANSCRIBE_PATH;
    }

    private String statusUrl(boolean warmup) {
        return sanitizedBaseUrl() + (warmup ? READY_PATH : HEALTH_PATH);
    }

    private String writeValue(Map<?, ?> body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ignored) {
            return String.valueOf(body);
        }
    }
}
