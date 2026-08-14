package com.deepthoughtnet.clinic.api.patientportal.auth;

import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalAccessLoginRequest;
import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalAccessLoginResponse;
import com.deepthoughtnet.clinic.patient.service.PatientPortalAccessRequestService;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessContext;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessGrantRecord;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient-portal/auth/access")
public class PatientPortalAccessLoginController {
    private final PatientPortalAccessRequestService accessRequestService;
    private final PatientPortalSessionTokenService sessionTokenService;

    public PatientPortalAccessLoginController(
            PatientPortalAccessRequestService accessRequestService,
            PatientPortalSessionTokenService sessionTokenService
    ) {
        this.accessRequestService = accessRequestService;
        this.sessionTokenService = sessionTokenService;
    }

    @PostMapping("/login")
    public PatientPortalAccessLoginResponse login(@Valid @RequestBody PatientPortalAccessLoginRequest request) {
        PatientPortalAccessGrantRecord grant = accessRequestService.authenticate(
                null,
                request.mobile(),
                request.accessCode(),
                toDomainContext(request.context())
        );
        String sessionToken = sessionTokenService.issuePatientToken(
                grant.subject(),
                grant.tenantId(),
                grant.patientId(),
                grant.patientMobile(),
                grant.patientDisplayName()
        );
        return new PatientPortalAccessLoginResponse(
                true,
                "Patient portal access verified.",
                grant.tenantId().toString(),
                grant.tenantCode(),
                grant.patientDisplayName(),
                sessionToken
        );
    }

    private PatientPortalAccessContext toDomainContext(com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpContext context) {
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
}
