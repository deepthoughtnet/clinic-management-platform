package com.deepthoughtnet.clinic.api.medicationsafety;

import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/consultations/{consultationId}/prescription/safety")
public class MedicationSafetyController {
    private static final Logger log = LoggerFactory.getLogger(MedicationSafetyController.class);
    private final MedicationSafetyService medicationSafetyService;
    private final MedicationSafetyReviewService medicationSafetyReviewService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public MedicationSafetyController(MedicationSafetyService medicationSafetyService,
                                      MedicationSafetyReviewService medicationSafetyReviewService,
                                      DoctorAssignmentSecurityService doctorAssignmentSecurityService) {
        this.medicationSafetyService = medicationSafetyService;
        this.medicationSafetyReviewService = medicationSafetyReviewService;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('consultation.read')")
    public MedicationSafetyEvaluationResult evaluate(@PathVariable UUID consultationId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationAccess(tenantId, consultationId);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return medicationSafetyService.evaluateForConsultation(tenantId, consultationId, actorAppUserId);
    }

    @PostMapping("/run")
    @PreAuthorize("@permissionChecker.hasPermission('prescription.create')")
    public MedicationSafetyReviewResponse runSafetyCheck(@PathVariable UUID consultationId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationAccess(tenantId, consultationId);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        String traceId = UUID.randomUUID().toString();
        MDC.put("medSafetyTraceId", traceId);
        try {
            log.info(
                    "MED-SAFETY-TRACE stage=CONTROLLER_START traceId={} tenantId={} consultationId={} actorAppUserId={}",
                    traceId,
                    tenantId,
                    consultationId,
                    actorAppUserId
            );
            MedicationSafetyReviewResponse response = medicationSafetyReviewService.runSafetyCheck(tenantId, consultationId, actorAppUserId);
            log.info(
                    "MED-SAFETY-TRACE stage=CONTROLLER_RESPONSE traceId={} tenantId={} consultationId={} prescriptionId={} reviewId={} decisionStatus={} requiredAction={} actionableFindingCount={}",
                    traceId,
                    tenantId,
                    consultationId,
                    response.prescriptionId(),
                    response.reviewId(),
                    response.decisionStatus(),
                    response.requiredAction(),
                    response.actionableFindingCount()
            );
            return response;
        } finally {
            MDC.remove("medSafetyTraceId");
        }
    }

    @GetMapping("/review")
    @PreAuthorize("@permissionChecker.hasPermission('consultation.read')")
    public MedicationSafetyReviewResponse review(@PathVariable UUID consultationId, @RequestParam(required = false) UUID prescriptionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationAccess(tenantId, consultationId);
        return medicationSafetyReviewService.getReview(tenantId, consultationId, prescriptionId);
    }

    @GetMapping("/evaluation")
    @PreAuthorize("@permissionChecker.hasPermission('consultation.read')")
    public MedicationSafetyEvaluationResult evaluation(@PathVariable UUID consultationId, @RequestParam(required = false) UUID prescriptionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationAccess(tenantId, consultationId);
        return medicationSafetyReviewService.getEvaluation(tenantId, consultationId, prescriptionId);
    }

    @PostMapping("/review")
    @PreAuthorize("@permissionChecker.hasPermission('prescription.create')")
    public MedicationSafetyReviewResponse submitReview(@PathVariable UUID consultationId, @org.springframework.web.bind.annotation.RequestBody MedicationSafetyReviewRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationAccess(tenantId, consultationId);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return medicationSafetyReviewService.submitReview(tenantId, consultationId, actorAppUserId, request);
    }
}
