package com.deepthoughtnet.clinic.api.consultation.document;

import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/consultations")
public class ConsultationDocumentController {
    private final ConsultationDocumentService documentService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public ConsultationDocumentController(ConsultationDocumentService documentService, DoctorAssignmentSecurityService doctorAssignmentSecurityService) {
        this.documentService = documentService;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    @PostMapping("/{consultationId}/generated-documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('consultation.update') or @permissionChecker.hasPermission('consultation.complete')")
    public GeneratedConsultationDocumentResponse generate(@PathVariable UUID consultationId, @Valid @RequestBody ConsultationGeneratedDocumentRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationAccess(tenantId, consultationId);
        return documentService.generate(tenantId, consultationId, request, RequestContextHolder.require().appUserId());
    }
}
