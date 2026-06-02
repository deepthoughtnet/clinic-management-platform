package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

public record PatientPortalOtpVerifyResponse(
        boolean verified,
        String message,
        String tenantId,
        String patientDisplayName,
        String patientSessionToken
) {
}
