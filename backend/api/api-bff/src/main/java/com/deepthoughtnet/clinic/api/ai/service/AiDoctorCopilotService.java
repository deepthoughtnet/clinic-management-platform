package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiEvidenceReference;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiDoctorCopilotService {
    private static final Logger log = LoggerFactory.getLogger(AiDoctorCopilotService.class);
    private static final String SAFETY_NOTICE = "This is an AI-generated draft. Doctor must verify before use.";

    private final AiOrchestrationService aiOrchestrationService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public AiDoctorCopilotService(AiOrchestrationService aiOrchestrationService,
                                  ObjectMapper objectMapper,
                                  @Value("${clinic.ai.enabled:false}") boolean enabled) {
        this.aiOrchestrationService = aiOrchestrationService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public AiDraftResponse draft(AiTaskType taskType,
                                 String promptTemplateCode,
                                 String useCaseCode,
                                 Map<String, Object> input,
                                 List<AiEvidenceReference> evidence) {
        if (!enabled) {
            return disabledResponse();
        }

        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorUserId = RequestContextHolder.require().appUserId();
        String correlationId = RequestContextHolder.require().correlationId();

        AiOrchestrationResponse response = aiOrchestrationService.complete(new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                tenantId,
                actorUserId,
                taskType,
                promptTemplateCode,
                input,
                evidence,
                null,
                0.1d,
                correlationId,
                useCaseCode
        ));

        List<String> warnings = new java.util.ArrayList<>();
        warnings.add(SAFETY_NOTICE);
        if (response.limitations() != null) {
            warnings.addAll(response.limitations());
        }

        Map<String, Object> structured = toStructuredData(response.structuredJson());
        String rawText = response.rawText();
        String draftText = response.outputText();
        String structuredJson = response.structuredJson();
        log.info("[NORMALIZED] taskType={} provider={} model={} rawChars={} outputChars={} structuredChars={} responseChars={} finishReason={} normalizedFinishReason={} parseStatus={} first300Chars=\"{}\" last300Chars=\"{}\"",
                taskType,
                response.provider(),
                response.model(),
                rawText == null ? 0 : rawText.length(),
                draftText == null ? 0 : draftText.length(),
                structuredJson == null ? 0 : structuredJson.length(),
                response.responseChars(),
                response.finishReason(),
                response.normalizedFinishReason(),
                response.parseStatus(),
                previewStart(rawText),
                previewEnd(rawText));
        log.debug("{} requestId={} provider={} model={} draftChars={} structuredKeys={} fallbackUsed={}",
                responsePrefix(taskType),
                response.requestId(),
                response.provider(),
                response.model(),
                response.outputText() == null ? 0 : response.outputText().length(),
                structured.keySet(),
                response.fallbackUsed());
        return new AiDraftResponse(
                true,
                response.fallbackUsed(),
                response.fallbackUsed() ? "AI fallback response used; verify manually." : "AI draft generated.",
                response.provider(),
                response.model(),
                response.outputText(),
                structured,
                response.confidence(),
                response.suggestedActions() == null ? List.of() : response.suggestedActions(),
                List.copyOf(warnings),
                response.finishReason(),
                response.normalizedFinishReason(),
                response.responseChars(),
                response.rawText(),
                response.parseStatus()
        );
    }

    private String previewStart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }

    private String previewEnd(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(normalized.length() - 300);
    }

    private Map<String, Object> toStructuredData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            return parsed == null ? Map.of("raw", json) : new LinkedHashMap<>(parsed);
        } catch (Exception ex) {
            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("raw", json);
            return structured;
        }
    }

    private AiDraftResponse disabledResponse() {
        return new AiDraftResponse(
                false,
                false,
                "AI copilot is disabled for this environment.",
                null,
                null,
                null,
                Map.of(),
                null,
                List.of(),
                List.of(SAFETY_NOTICE),
                null,
                "UNKNOWN",
                0,
                null,
                "FAILED"
        );
    }

    private String responsePrefix(AiTaskType taskType) {
        if (taskType == AiTaskType.SYMPTOMS_DIAGNOSIS_DRAFT) {
            return "AI_DIAGNOSIS_RESPONSE";
        }
        if (taskType == AiTaskType.PRESCRIPTION_TEMPLATE_SUGGESTION) {
            return "AI_MEDICINE_RESPONSE";
        }
        if (taskType == AiTaskType.CLINICAL_REASONING) {
            return "AI_REASONING_RESPONSE";
        }
        return "AI_RESPONSE";
    }
}
