package com.deepthoughtnet.clinic.api.clinicalintake;

import com.deepthoughtnet.clinic.api.clinicalintake.dto.ClinicalIntakeRequest;
import com.deepthoughtnet.clinic.api.clinicalintake.dto.ClinicalIntakeResponse;
import com.deepthoughtnet.clinic.api.clinicalintake.service.ClinicalIntakeService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/patients/{patientId}/clinical-intake")
public class ClinicalIntakeController {
    private final ClinicalIntakeService clinicalIntakeService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public ClinicalIntakeController(ClinicalIntakeService clinicalIntakeService,
                                    DoctorAssignmentSecurityService doctorAssignmentSecurityService) {
        this.clinicalIntakeService = clinicalIntakeService;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    @GetMapping("/latest")
    @PreAuthorize("@permissionChecker.hasPermission('clinical.intake.read') or @permissionChecker.hasPermission('patient.read') or @permissionChecker.hasPermission('consultation.read')")
    public ClinicalIntakeResponse latest(@PathVariable UUID patientId,
                                         @RequestParam(required = false) UUID appointmentId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, patientId);
        return clinicalIntakeService.latest(tenantId, patientId, appointmentId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Clinical intake not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('clinical.intake.write') or @permissionChecker.hasPermission('appointment.manage') or @permissionChecker.hasPermission('consultation.update')")
    public ClinicalIntakeResponse save(@PathVariable UUID patientId, @Valid @RequestBody ClinicalIntakeRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, patientId);
        return clinicalIntakeService.save(tenantId, patientId, request, RequestContextHolder.require().appUserId());
    }
}
