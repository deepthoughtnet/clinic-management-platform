package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationAskRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AiConsultationAskService {
    private static final Logger log = LoggerFactory.getLogger(AiConsultationAskService.class);
    private final AiDoctorCopilotService copilotService;
    private final ClinicalContextService clinicalContextService;

    public AiConsultationAskService(AiDoctorCopilotService copilotService, ClinicalContextService clinicalContextService) {
        this.copilotService = copilotService;
        this.clinicalContextService = clinicalContextService;
    }

    public AiDraftResponse ask(AiConsultationAskRequest request) {
        ClinicalContextResponse context = clinicalContextService.buildClinicalContext(
                RequestContextHolder.requireTenantId(),
                request.patientId(),
                request.consultationId()
        );
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("consultationId", request.consultationId());
        input.put("patientId", request.patientId());
        input.put("prompt", request.prompt());
        input.put("aiPromptContext", context.aiPromptContext());
        input.put("clinicalContextSummary", context.aiSummary());
        log.info("[AI-CHAT-CONTEXT] consultationId={} patientId={} canonicalContextChars={} promptContextChars={} summaryChars={} questionChars={}",
                request.consultationId(),
                request.patientId(),
                context.clinicalContextJson() == null ? 0 : context.clinicalContextJson().length(),
                context.aiPromptContext() == null ? 0 : context.aiPromptContext().length(),
                context.aiSummary() == null ? 0 : context.aiSummary().length(),
                request.prompt() == null ? 0 : request.prompt().length());

        return copilotService.draft(
                AiTaskType.GENERIC_COPILOT,
                "clinic.consultation.ask.v1",
                "consultation.ask",
                input,
                List.of()
        );
    }
}
