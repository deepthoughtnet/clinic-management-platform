package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PatientPortalAccessCodeReissueRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be a valid 10-digit Indian mobile number.")
        String mobile,
        @Valid PatientPortalOtpContext context
) {
}
