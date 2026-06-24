package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

public record PatientPortalOtpVerifyResponse(
        boolean verified,
        boolean patientExists,
        boolean registrationRequired,
        String message,
        String tenantId,
        String tenantCode,
        String patientDisplayName,
        String patientSessionToken,
        String registrationSessionToken
) {
}
