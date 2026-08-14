package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record PatientPortalAccessLoginRequest(
        @NotBlank String mobile,
        @NotBlank String accessCode,
        @Valid PatientPortalOtpContext context
) {
}
