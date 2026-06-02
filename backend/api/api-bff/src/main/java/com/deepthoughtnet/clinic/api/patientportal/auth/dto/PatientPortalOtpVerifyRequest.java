package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PatientPortalOtpVerifyRequest(
        @NotBlank String tenantCode,
        @NotBlank String phone,
        @NotBlank String otp
) {
}
