package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

public record PatientPortalOtpRequestResponse(
        boolean accepted,
        String message,
        long expiresInSeconds,
        long resendAvailableInSeconds,
        String devOtp
) {
}
