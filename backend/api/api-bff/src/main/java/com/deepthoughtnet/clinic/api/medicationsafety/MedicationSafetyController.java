package com.deepthoughtnet.clinic.api.medicationsafety;

import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/consultations/{consultationId}/prescription/safety")
public class MedicationSafetyController {
    private final MedicationSafetyService medicationSafetyService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public MedicationSafetyController(MedicationSafetyService medicationSafetyService,
                                      DoctorAssignmentSecurityService doctorAssignmentSecurityService) {
        this.medicationSafetyService = medicationSafetyService;
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
}
