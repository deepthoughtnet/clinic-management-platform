package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiProviderRouter;
import com.deepthoughtnet.clinic.llm.spi.AiProviderException;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
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

    private final AiProviderRouter aiProviderRouter;
    private final ObjectMapper objectMapper;
    private final AivaResponseComposerProperties properties;

    public PatientPortalCareAiResponseComposerService(
            AiProviderRouter aiProviderRouter,
            ObjectMapper objectMapper,
            AivaResponseComposerProperties properties
    ) {
        this.aiProviderRouter = aiProviderRouter;
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
        if (!properties.isEnabled() || aiProviderRouter == null || !StringUtils.hasText(normalizedRaw)) {
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
        UUID requestId = UUID.randomUUID();

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

        List<AiProvider> providers = aiProviderRouter.resolveCandidates(AiTaskType.GENERIC_COPILOT);
        if (providers.isEmpty()) {
            trace(true, workflow, responseType, language, null, true, normalizedRaw.length(), normalizedRaw.length());
            return rawResponseText;
        }

        try (ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < providers.size(); i += 1) {
                AiProvider provider = providers.get(i);
                AiProviderResponse response;
                try {
                    response = CompletableFuture.supplyAsync(() -> provider.complete(new AiProviderRequest(
                                    request,
                                    null,
                                    buildSystemPrompt(),
                                    buildUserPrompt(normalizedRaw, responseType, language, workflow, safeStructuredFacts),
                                    input,
                                    List.of(),
                                    requestId
                            )), executor)
                            .get(Math.max(1, properties.getTimeoutSeconds()), TimeUnit.SECONDS);
                } catch (TimeoutException ex) {
                    log.warn("AIVA_RESPONSE_COMPOSER_TRACE timeout workflow={} responseType={} language={} provider={} timeoutSeconds={}",
                            safe(workflow),
                            safe(responseType),
                            safe(language),
                            provider.providerName(),
                            properties.getTimeoutSeconds());
                    continue;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    trace(true, workflow, responseType, language, provider.providerName(), true, normalizedRaw.length(), normalizedRaw.length());
                    return rawResponseText;
                } catch (AiProviderException ex) {
                    log.debug("AIVA_RESPONSE_COMPOSER_TRACE provider_failed workflow={} responseType={} language={} provider={} reason={}",
                            safe(workflow),
                            safe(responseType),
                            safe(language),
                            provider.providerName(),
                            ex.toString());
                    continue;
                } catch (Exception ex) {
                    log.debug("AIVA_RESPONSE_COMPOSER_TRACE provider_failed workflow={} responseType={} language={} provider={} reason={}",
                            safe(workflow),
                            safe(responseType),
                            safe(language),
                            provider.providerName(),
                            ex.toString());
                    continue;
                }

                String polished = normalizeProviderOutput(response == null ? null : response.outputText());
                if (!StringUtils.hasText(polished)) {
                    trace(true, workflow, responseType, language, provider.providerName(), true, normalizedRaw.length(), normalizedRaw.length());
                    return rawResponseText;
                }
                trace(true, workflow, responseType, language, provider.providerName(), i > 0, normalizedRaw.length(), polished.length());
                if (log.isDebugEnabled()) {
                    log.debug("AIVA_RESPONSE_COMPOSER_TRACE raw=\"{}\" polished=\"{}\"",
                            preview(normalizedRaw),
                            preview(polished));
                }
                return polished;
            }
        } catch (Exception ex) {
            trace(true, workflow, responseType, language, "error", true, normalizedRaw.length(), normalizedRaw.length());
            log.debug("AIVA_RESPONSE_COMPOSER_TRACE fallback workflow={} responseType={} language={} reason={}",
                    safe(workflow),
                    safe(responseType),
                    safe(language),
                    ex.toString());
            return rawResponseText;
        }

        trace(true, workflow, responseType, language, "fallback", true, normalizedRaw.length(), normalizedRaw.length());
        return rawResponseText;
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

    private String normalizeProviderOutput(String outputText) {
        if (!StringUtils.hasText(outputText)) {
            return null;
        }
        String output = outputText.trim();
        if (looksLikeJson(output)) {
            return null;
        }
        if (isWrappedInQuotes(output)) {
            String unwrapped = output.substring(1, output.length() - 1).trim();
            return StringUtils.hasText(unwrapped) ? unwrapped.replace("\\\"", "\"") : null;
        }
        if (isPartialQuote(output)) {
            return null;
        }
        return output;
    }

    private boolean looksLikeJson(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private boolean isWrappedInQuotes(String value) {
        if (!StringUtils.hasText(value) || value.length() < 2) {
            return false;
        }
        return (value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"));
    }

    private boolean isPartialQuote(String value) {
        if (!StringUtils.hasText(value) || value.length() < 2) {
            return false;
        }
        return (value.startsWith("\"") && !value.endsWith("\""))
                || (!value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && !value.endsWith("'"))
                || (!value.startsWith("'") && value.endsWith("'"));
    }

    private String buildSystemPrompt() {
        return """
                You are a professional, warm clinic assistant.
                Rewrite the assistant response as plain text only.
                Keep it concise.
                Do not add medical advice.
                Do not invent facts, slots, dates, prices, clinic names, or appointments.
                Preserve confirmation prompts exactly in meaning.
                For hi-IN, use natural Hindi or Hinglish.
                Return plain text only.
                """;
    }

    private String buildUserPrompt(String rawResponseText,
                                   String responseType,
                                   String language,
                                   String workflow,
                                   SafeStructuredFacts safeStructuredFacts) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("rawResponseText: ").append(rawResponseText).append('\n');
        prompt.append("responseType: ").append(safe(responseType)).append('\n');
        prompt.append("language: ").append(safe(language)).append('\n');
        prompt.append("workflow: ").append(safe(workflow)).append('\n');
        prompt.append("safeStructuredFacts: ").append(safeFactsJson(safeStructuredFacts)).append('\n');
        prompt.append("Rewrite this as a professional clinic assistant response. Return plain text only.");
        return prompt.toString();
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
