package com.deepthoughtnet.clinic.api.consultation;

import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiPrescriptionSuggestionRequest;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiPrescriptionSuggestionResponse;
import com.deepthoughtnet.clinic.api.consultation.service.ConsultationAiPrescriptionSuggestionService;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/consultations")
public class ConsultationAiPrescriptionSuggestionController {
    private final ConsultationAiPrescriptionSuggestionService service;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public ConsultationAiPrescriptionSuggestionController(
            ConsultationAiPrescriptionSuggestionService service,
            DoctorAssignmentSecurityService doctorAssignmentSecurityService
    ) {
        this.service = service;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    @GetMapping("/{id}/ai-prescription-suggestion")
    @PreAuthorize("@permissionChecker.hasPermission('consultation.read')")
    public ConsultationAiPrescriptionSuggestionResponse get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationAccess(tenantId, id);
        return service.get(tenantId, id);
    }

    @PatchMapping("/{id}/ai-prescription-suggestion")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@permissionChecker.hasPermission('consultation.update')")
    public ConsultationAiPrescriptionSuggestionResponse save(@PathVariable UUID id, @Valid @RequestBody ConsultationAiPrescriptionSuggestionRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationAccess(tenantId, id);
        return service.save(tenantId, id, request);
    }
}
