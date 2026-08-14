package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record PatientPortalAccessRequestSubmitRequest(
        @NotBlank String fullName,
        @NotBlank String mobile,
        String email,
        String note,
        @Valid PatientPortalOtpContext context
) {
}
