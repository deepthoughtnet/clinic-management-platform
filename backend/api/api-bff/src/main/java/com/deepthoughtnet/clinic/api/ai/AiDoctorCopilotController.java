package com.deepthoughtnet.clinic.api.ai;

import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationNotesRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDiagnosisSuggestionRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiClinicalAnalyticsResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiClinicalSummaryRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiPatientInstructionsRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiPatientSummaryRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiPrescriptionTemplateRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiStatusResponse;
import com.deepthoughtnet.clinic.api.ai.service.AiClinicalAnalyticsService;
import com.deepthoughtnet.clinic.api.ai.service.AiClinicalSummaryService;
import com.deepthoughtnet.clinic.api.ai.service.AiConsultationDraftService;
import com.deepthoughtnet.clinic.api.ai.service.AiPatientSummaryService;
import com.deepthoughtnet.clinic.api.ai.service.AiStatusService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiRequestAuditQueryService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiRecentRequestRecord;
import com.deepthoughtnet.clinic.identity.service.TenantModuleEntitlementService;
import com.deepthoughtnet.clinic.platform.core.module.ModuleKeys;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final AiClinicalSummaryService aiClinicalSummaryService;
    private final AiClinicalAnalyticsService aiClinicalAnalyticsService;
    private final AiConsultationDraftService aiConsultationDraftService;
    private final AiRequestAuditQueryService aiRequestAuditQueryService;
    private final TenantModuleEntitlementService moduleEntitlementService;
    private final AiStatusService aiStatusService;

    public AiDoctorCopilotController(AiPatientSummaryService aiPatientSummaryService,
                                     AiClinicalSummaryService aiClinicalSummaryService,
                                     AiClinicalAnalyticsService aiClinicalAnalyticsService,
                                     AiConsultationDraftService aiConsultationDraftService,
                                     AiRequestAuditQueryService aiRequestAuditQueryService,
                                     TenantModuleEntitlementService moduleEntitlementService,
                                     AiStatusService aiStatusService) {
        this.aiPatientSummaryService = aiPatientSummaryService;
        this.aiClinicalSummaryService = aiClinicalSummaryService;
        this.aiClinicalAnalyticsService = aiClinicalAnalyticsService;
        this.aiConsultationDraftService = aiConsultationDraftService;
        this.aiRequestAuditQueryService = aiRequestAuditQueryService;
        this.moduleEntitlementService = moduleEntitlementService;
        this.aiStatusService = aiStatusService;
    }

    @GetMapping("/status")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS + " or @permissionChecker.hasPermission('ai_copilot.read') or @permissionChecker.hasPermission('ai_copilot.clinic.read')")
    public AiStatusResponse status() {
        return aiStatusService.status(RequestContextHolder.requireTenantId());
    }

    @PostMapping("/patient-summary")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public AiDraftResponse patientSummary(@RequestBody AiPatientSummaryRequest request) {
        requireAiReady();
        return aiPatientSummaryService.summarizePatient(request);
    }

    @PostMapping("/consultation/structure-notes")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public AiDraftResponse structureNotes(@RequestBody AiConsultationNotesRequest request) {
        requireAiReady();
        return aiConsultationDraftService.structureNotes(request);
    }

    @PostMapping("/consultation/suggest-diagnosis")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public AiDraftResponse suggestDiagnosis(@RequestBody AiDiagnosisSuggestionRequest request) {
        requireAiReady();
        return aiConsultationDraftService.suggestDiagnosis(request);
    }

    @PostMapping("/prescription/suggest-template")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public AiDraftResponse suggestPrescriptionTemplate(@RequestBody AiPrescriptionTemplateRequest request) {
        requireAiReady();
        return aiConsultationDraftService.suggestPrescriptionTemplate(request);
    }

    @PostMapping("/patient-instructions")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public AiDraftResponse patientInstructions(@RequestBody AiPatientInstructionsRequest request) {
        requireAiReady();
        return aiPatientSummaryService.patientInstructions(request);
    }

    @PostMapping("/clinical-summary")
    @PreAuthorize(AI_COPILOT_RUN_ACCESS)
    public AiDraftResponse clinicalSummary(@RequestBody AiClinicalSummaryRequest request) {
        requireAiReady();
        return aiClinicalSummaryService.summarize(request);
    }

    @GetMapping("/analytics")
    @PreAuthorize("@permissionChecker.hasPermission('ai_copilot.read') or @permissionChecker.hasPermission('ai_copilot.clinic.read') or @permissionChecker.hasPermission('audit.read')")
    public AiClinicalAnalyticsResponse analytics() {
        requireAiModule();
        return aiClinicalAnalyticsService.summarize(RequestContextHolder.requireTenantId());
    }

    @GetMapping("/audit/recent")
    @PreAuthorize("@permissionChecker.hasPermission('ai_copilot.read') or @permissionChecker.hasPermission('ai_copilot.clinic.read') or @permissionChecker.hasPermission('audit.read')")
    public List<AiRecentRequestRecord> recentAudit() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return aiRequestAuditQueryService.recent(tenantId, AiProductCode.CLINIC);
    }

    private void requireAiReady() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        aiStatusService.requireProviderReady(tenantId);
    }

    private void requireAiModule() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        moduleEntitlementService.requireModuleEnabled(tenantId, ModuleKeys.AI_COPILOT);
    }
}
