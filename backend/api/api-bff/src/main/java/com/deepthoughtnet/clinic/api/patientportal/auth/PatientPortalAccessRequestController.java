package com.deepthoughtnet.clinic.api.patientportal.auth;

import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalAccessRequestResponse;
import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalAccessRequestSubmitRequest;
import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpContext;
import com.deepthoughtnet.clinic.patient.service.PatientPortalAccessRequestService;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessContext;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestCommand;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestRecord;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient-portal/auth/access-requests")
public class PatientPortalAccessRequestController {
    private final PatientPortalAccessRequestService accessRequestService;

    public PatientPortalAccessRequestController(PatientPortalAccessRequestService accessRequestService) {
        this.accessRequestService = accessRequestService;
    }

    @PostMapping
    public PatientPortalAccessRequestResponse submit(@Valid @RequestBody PatientPortalAccessRequestSubmitRequest request) {
        PatientPortalAccessRequestRecord record = accessRequestService.submit(new PatientPortalAccessRequestCommand(
                request.fullName(),
                request.mobile(),
                request.email(),
                request.note(),
                toDomainContext(request.context())
        ));
        return toResponse(record);
    }

    private PatientPortalAccessContext toDomainContext(PatientPortalOtpContext context) {
        if (context == null) {
            return null;
        }
        return new PatientPortalAccessContext(
                context.clinicId(),
                context.clinicSlug(),
                context.tenantId(),
                context.doctorId(),
                context.appointmentIntent()
        );
    }

    private PatientPortalAccessRequestResponse toResponse(PatientPortalAccessRequestRecord record) {
        return new PatientPortalAccessRequestResponse(
                record.id(),
                record.tenantId(),
                record.tenantCode(),
                record.tenantName(),
                record.requestType().name(),
                record.fullName(),
                record.mobile(),
                record.email(),
                record.note(),
                record.status().name(),
                record.rejectionReason(),
                record.linkedPatientId(),
                record.linkedPatientDisplayName(),
                record.reviewedBy(),
                record.reviewedByDisplayName(),
                record.temporaryAccessCode(),
                record.requestedAt(),
                record.reviewedAt(),
                record.approvedAt(),
                record.activatedAt(),
                record.revokedAt(),
                record.accessCodeExpiresAt(),
                record.createdAt(),
                record.updatedAt(),
                record.version()
        );
    }
}
