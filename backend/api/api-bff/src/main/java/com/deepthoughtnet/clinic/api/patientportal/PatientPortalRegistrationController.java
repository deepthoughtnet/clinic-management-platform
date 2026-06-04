package com.deepthoughtnet.clinic.api.patientportal;

import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalRegistrationRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalRegistrationResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient-portal/registration")
@PreAuthorize("@permissionChecker.hasRole('PATIENT_REGISTRATION')")
public class PatientPortalRegistrationController {
    private final PatientPortalRegistrationService patientPortalRegistrationService;

    public PatientPortalRegistrationController(PatientPortalRegistrationService patientPortalRegistrationService) {
        this.patientPortalRegistrationService = patientPortalRegistrationService;
    }

    @PostMapping("/complete")
    public PatientPortalRegistrationResponse complete(@Valid @RequestBody PatientPortalRegistrationRequest request) {
        return patientPortalRegistrationService.complete(request);
    }
}
