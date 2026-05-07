package com.deepthoughtnet.clinic.api.ai;

import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationNotesRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDiagnosisSuggestionRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiPatientInstructionsRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiPatientSummaryRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiPrescriptionTemplateRequest;
import com.deepthoughtnet.clinic.api.ai.service.AiConsultationDraftService;
import com.deepthoughtnet.clinic.api.ai.service.AiPatientSummaryService;
import com.deepthoughtnet.clinic.identity.service.TenantModuleEntitlementService;
import com.deepthoughtnet.clinic.platform.core.module.ModuleKeys;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiDoctorCopilotController {
    private static final String AI_COPILOT_RUN_ACCESS = """
            @permissionChecker.hasPermission('ai_copilot.clinic.run') or
            @permissionChecker.hasPermission('ai_copilot.run')
            """;

    private final AiPatientSummaryService aiPatientSummaryService;
    private final AiConsultationDraftService aiConsultationDraftService;
    private final TenantModuleEntitlementService moduleEntitlementService;

    public AiDoctorCopilotController(AiPatientSummaryService aiPatientSummaryService,
                                     AiConsultationDraftService aiConsultationDraftService,
                                     TenantModuleEntitlementService moduleEntitlementService) {
        this.aiPatientSummaryService = aiPatientSummaryService;
        this.aiConsultationDraftService = aiConsultationDraftService;
        this.moduleEntitlementService = moduleEntitlementService;
    }

    @PostMapping("/patient-summary")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public AiDraftResponse patientSummary(@RequestBody AiPatientSummaryRequest request) {
        requireAiModule();
        return aiPatientSummaryService.summarizePatient(request);
    }

    @PostMapping("/consultation/structure-notes")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public AiDraftResponse structureNotes(@RequestBody AiConsultationNotesRequest request) {
        requireAiModule();
        return aiConsultationDraftService.structureNotes(request);
    }

    @PostMapping("/consultation/suggest-diagnosis")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public AiDraftResponse suggestDiagnosis(@RequestBody AiDiagnosisSuggestionRequest request) {
        requireAiModule();
        return aiConsultationDraftService.suggestDiagnosis(request);
    }

    @PostMapping("/prescription/suggest-template")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public AiDraftResponse suggestPrescriptionTemplate(@RequestBody AiPrescriptionTemplateRequest request) {
        requireAiModule();
        return aiConsultationDraftService.suggestPrescriptionTemplate(request);
    }

    @PostMapping("/patient-instructions")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public AiDraftResponse patientInstructions(@RequestBody AiPatientInstructionsRequest request) {
        requireAiModule();
        return aiPatientSummaryService.patientInstructions(request);
    }

    private void requireAiModule() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        moduleEntitlementService.requireModuleEnabled(tenantId, ModuleKeys.AI_COPILOT);
    }
}
