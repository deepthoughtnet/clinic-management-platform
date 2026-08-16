package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatientPortalAccessRequestSubmitRequest(
        @NotBlank
        @Size(min = 2, max = 120)
        String fullName,
        @NotBlank
        @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be a valid 10-digit Indian mobile number.")
        String mobile,
        @Email
        @Size(max = 254)
        String email,
        @Size(max = 500)
        String note,
        @Valid PatientPortalOtpContext context
) {
}
