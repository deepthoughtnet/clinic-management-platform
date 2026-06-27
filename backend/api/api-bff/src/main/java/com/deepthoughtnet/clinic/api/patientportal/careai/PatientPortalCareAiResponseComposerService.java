package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PatientPortalCareAiResponseComposerService {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalCareAiResponseComposerService.class);
    private static final String TEMPLATE_CODE = "patient.portal.careai.response.composer.v1";
    private static final String USE_CASE_CODE = "patient-portal-careai-response-composer";
    private static final double TEMPERATURE = 0.2d;
    private static final int MAX_OUTPUT_TOKENS = 384;

    private final AiOrchestrationService aiOrchestrationService;
    private final ObjectMapper objectMapper;
    private final AivaResponseComposerProperties properties;

    public PatientPortalCareAiResponseComposerService(
            AiOrchestrationService aiOrchestrationService,
            ObjectMapper objectMapper,
            AivaResponseComposerProperties properties
    ) {
        this.aiOrchestrationService = aiOrchestrationService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String compose(
            String rawResponseText,
            String responseType,
            String language,
            String workflow,
            SafeStructuredFacts safeStructuredFacts
    ) {
        String normalizedRaw = rawResponseText == null ? "" : rawResponseText.trim();
        if (!properties.isEnabled() || !properties.isVoiceOnly() || aiOrchestrationService == null || !StringUtils.hasText(normalizedRaw)) {
            trace(false, workflow, responseType, language, null, false, normalizedRaw.length(), normalizedRaw.length());
            return rawResponseText;
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("rawResponseText", normalizedRaw);
        input.put("responseType", safe(responseType));
        input.put("language", safe(language));
        input.put("workflow", safe(workflow));
        input.put("safeStructuredFactsJson", safeFactsJson(safeStructuredFacts));
        input.put("safeStructuredFacts", safeStructuredFacts == null ? Map.of() : safeStructuredFacts.toMap());

        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.GENERIC,
                requestTenantId(),
                requestActorUserId(),
                AiTaskType.GENERIC_COPILOT,
                TEMPLATE_CODE,
                input,
                List.of(),
                MAX_OUTPUT_TOKENS,
                TEMPERATURE,
                requestCorrelationId(),
                USE_CASE_CODE
        );

        try (ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            AiOrchestrationResponse response = CompletableFuture.supplyAsync(() -> aiOrchestrationService.complete(request), executor)
                    .get(Math.max(1, properties.getTimeoutSeconds()), TimeUnit.SECONDS);
            String polished = extractPolishedText(response);
            if (!StringUtils.hasText(polished)) {
                polished = normalizedRaw;
            }
            trace(true, workflow, responseType, language, response == null ? null : response.provider(), response != null && response.fallbackUsed(), normalizedRaw.length(), polished.length());
            if (log.isDebugEnabled()) {
                log.debug("AIVA_RESPONSE_COMPOSER_TRACE raw=\"{}\" polished=\"{}\"",
                        preview(normalizedRaw),
                        preview(polished));
            }
            return polished;
        } catch (TimeoutException ex) {
            trace(true, workflow, responseType, language, "timeout", true, normalizedRaw.length(), normalizedRaw.length());
            log.warn("AIVA_RESPONSE_COMPOSER_TRACE timeout workflow={} responseType={} language={} timeoutSeconds={}",
                    safe(workflow),
                    safe(responseType),
                    safe(language),
                    properties.getTimeoutSeconds());
            return rawResponseText;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            trace(true, workflow, responseType, language, "interrupted", true, normalizedRaw.length(), normalizedRaw.length());
            return rawResponseText;
        } catch (Exception ex) {
            trace(true, workflow, responseType, language, "error", true, normalizedRaw.length(), normalizedRaw.length());
            log.debug("AIVA_RESPONSE_COMPOSER_TRACE fallback workflow={} responseType={} language={} reason={}",
                    safe(workflow),
                    safe(responseType),
                    safe(language),
                    ex.toString());
            return rawResponseText;
        }
    }

    public SafeStructuredFacts safeStructuredFacts(
            String doctorName,
            String clinicName,
            String date,
            String time,
            List<String> slots,
            List<String> appointments,
            boolean confirmationRequired,
            String actionType
    ) {
        return new SafeStructuredFacts(doctorName, clinicName, date, time, slots, appointments, confirmationRequired, actionType);
    }

    private String extractPolishedText(AiOrchestrationResponse response) {
        if (response == null || !StringUtils.hasText(response.outputText())) {
            return null;
        }
        String output = response.outputText().trim();
        String extracted = extractFromJson(output);
        return StringUtils.hasText(extracted) ? extracted.trim() : output;
    }

    private String extractFromJson(String output) {
        String candidate = stripFences(output);
        if (!looksLikeJson(candidate)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(candidate);
            if (root == null || !root.isObject()) {
                return null;
            }
            for (String field : List.of("answer", "outputText", "summary")) {
                JsonNode node = root.get(field);
                if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
                    return node.asText();
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String stripFences(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private boolean looksLikeJson(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private String safeFactsJson(SafeStructuredFacts safeStructuredFacts) {
        if (safeStructuredFacts == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(safeStructuredFacts.toMap());
        } catch (Exception ex) {
            return "{}";
        }
    }

    private UUID requestTenantId() {
        return RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value();
    }

    private UUID requestActorUserId() {
        return RequestContextHolder.get() == null ? null : RequestContextHolder.get().appUserId();
    }

    private String requestCorrelationId() {
        return RequestContextHolder.get() == null ? null : RequestContextHolder.get().correlationId();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void trace(
            boolean enabled,
            String workflow,
            String responseType,
            String language,
            String provider,
            boolean fallbackUsed,
            int rawChars,
            int polishedChars
    ) {
        log.info("AIVA_RESPONSE_COMPOSER_TRACE enabled={} workflow={} responseType={} language={} provider={} fallbackUsed={} rawChars={} polishedChars={}",
                enabled,
                safe(workflow),
                safe(responseType),
                safe(language),
                safe(provider),
                fallbackUsed,
                rawChars,
                polishedChars);
    }

    private String preview(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 157) + "...";
    }

    public record SafeStructuredFacts(
            String doctorName,
            String clinicName,
            String date,
            String time,
            List<String> slots,
            List<String> appointments,
            boolean confirmationRequired,
            String actionType
    ) {
        public SafeStructuredFacts {
            slots = slots == null ? List.of() : List.copyOf(slots);
            appointments = appointments == null ? List.of() : List.copyOf(appointments);
        }

        Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("doctorName", doctorName);
            data.put("clinicName", clinicName);
            data.put("date", date);
            data.put("time", time);
            data.put("slots", slots);
            data.put("appointments", appointments);
            data.put("confirmationRequired", confirmationRequired);
            data.put("actionType", actionType);
            return data;
        }
    }
}
