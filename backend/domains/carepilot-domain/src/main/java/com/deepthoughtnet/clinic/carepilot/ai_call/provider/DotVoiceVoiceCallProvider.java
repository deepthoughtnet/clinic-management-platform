package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import com.deepthoughtnet.clinic.voice.spi.VoiceCallProvider;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallRequest;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallResult;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/** Real DotVoice REST adapter for platform provider certification. */
public class DotVoiceVoiceCallProvider implements VoiceCallProvider {
    private static final Logger log = LoggerFactory.getLogger(DotVoiceVoiceCallProvider.class);
    private final DotVoiceProperties properties;
    private final DotVoiceHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DotVoiceVoiceCallProvider(DotVoiceProperties properties, DotVoiceHttpClient httpClient) {
        this(properties, httpClient, new ObjectMapper());
    }

    DotVoiceVoiceCallProvider(DotVoiceProperties properties, DotVoiceHttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Emits only non-sensitive readiness facts so a deployment can distinguish
     * binding/configuration problems from a provider outage.  Stage 1 is PSTN
     * origination only; callback/media configuration is deliberately excluded.
     */
    @PostConstruct
    void logReadiness() {
        log.info("DOTVOICE_READINESS enabled={} apiKeyPresent={} baseUrlPresent={} callsPath={} "
                        + "fromNumberPresent={} trunkPresent={} timeoutValid={} callbackRequired=false ready={}",
                properties.isEnabled(), hasText(properties.getApiKey()), hasText(properties.getBaseUrl()),
                properties.getCallsPath(), hasText(properties.getFromNumber()), hasText(properties.getTrunk()),
                properties.getTimeoutMs() > 0, isReady());
    }

    @Override
    public String providerName() { return "dotvoice"; }

    @Override
    public boolean isReady() { return properties.isConfigured(); }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    public VoiceCallResult placeCall(VoiceCallRequest request) {
        OffsetDateTime started = OffsetDateTime.now();
        if (!isReady()) {
            return result(VoiceCallStatus.FAILED, null, "NOT_CONFIGURED", started);
        }
        if (request == null || !StringUtils.hasText(request.phoneNumber())) {
            return result(VoiceCallStatus.FAILED, null, "RECIPIENT_INVALID", started);
        }
        DotVoiceHttpResponse response = httpClient.createCall(properties, request.phoneNumber().trim(), request.script());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return result(VoiceCallStatus.FAILED, extractCallId(response.body()), httpFailure(response.statusCode()), started);
        }
        JsonNode body = read(response.body());
        String callId = text(body, "call_id", "callId", "id");
        if (!StringUtils.hasText(callId)) {
            return result(VoiceCallStatus.FAILED, null, "INVALID_PROVIDER_RESPONSE", started);
        }
        String status = text(body, "status", "state");
        return new VoiceCallResult(normalizeStatus(status), providerName(), callId,
                null, started, OffsetDateTime.now(), null);
    }

    private VoiceCallResult result(VoiceCallStatus status, String callId, String failure, OffsetDateTime started) {
        return new VoiceCallResult(status, providerName(), callId, failure, started, OffsetDateTime.now(), null);
    }

    private String httpFailure(int status) {
        return status == 401 || status == 403 ? "AUTHENTICATION_FAILED"
                : status == 599 ? "TIMEOUT_OR_TRANSPORT_ERROR" : "PROVIDER_HTTP_" + status;
    }

    private VoiceCallStatus normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) return VoiceCallStatus.QUEUED;
        return switch (value.trim().toLowerCase()) {
            case "queued", "initiated", "initializing", "ringing" -> VoiceCallStatus.QUEUED;
            case "answered", "in_progress", "in-progress", "active" -> VoiceCallStatus.IN_PROGRESS;
            case "completed", "complete", "ended" -> VoiceCallStatus.COMPLETED;
            case "busy" -> VoiceCallStatus.BUSY;
            case "no_answer", "no-answer", "noanswer" -> VoiceCallStatus.NO_ANSWER;
            case "cancelled", "canceled" -> VoiceCallStatus.CANCELLED;
            case "failed", "error", "rejected" -> VoiceCallStatus.FAILED;
            default -> VoiceCallStatus.QUEUED;
        };
    }

    private String extractCallId(String body) { return text(read(body), "call_id", "callId", "id"); }

    private JsonNode read(String body) {
        if (!StringUtils.hasText(body)) return objectMapper.createObjectNode();
        try { return objectMapper.readTree(body); } catch (Exception ex) { return objectMapper.createObjectNode(); }
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node == null ? null : node.get(name);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) return value.asText();
        }
        return null;
    }
}
