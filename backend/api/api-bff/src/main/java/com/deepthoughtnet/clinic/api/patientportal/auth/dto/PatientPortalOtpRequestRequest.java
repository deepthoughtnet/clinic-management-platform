package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PatientPortalOtpRequestRequest(
        @NotBlank String tenantCode,
        @NotBlank String phone
) {
}
