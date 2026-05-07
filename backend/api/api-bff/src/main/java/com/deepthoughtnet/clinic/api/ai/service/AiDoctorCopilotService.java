package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiEvidenceReference;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiDoctorCopilotService {
    private static final String SAFETY_NOTICE = "This is an AI-generated draft. Doctor must verify before use.";

    private final AiOrchestrationService aiOrchestrationService;
    private final boolean enabled;

    public AiDoctorCopilotService(AiOrchestrationService aiOrchestrationService,
                                  @Value("${clinic.ai.enabled:false}") boolean enabled) {
        this.aiOrchestrationService = aiOrchestrationService;
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
                800,
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
        return new AiDraftResponse(
                true,
                response.fallbackUsed(),
                response.fallbackUsed() ? "AI fallback response used; verify manually." : "AI draft generated.",
                response.outputText(),
                structured,
                response.confidence(),
                response.suggestedActions() == null ? List.of() : response.suggestedActions(),
                List.copyOf(warnings)
        );
    }

    private Map<String, Object> toStructuredData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("raw", json);
        return structured;
    }

    private AiDraftResponse disabledResponse() {
        return new AiDraftResponse(
                false,
                false,
                "AI copilot is disabled for this environment.",
                null,
                Map.of(),
                null,
                List.of(),
                List.of(SAFETY_NOTICE)
        );
    }
}
