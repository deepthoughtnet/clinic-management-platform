package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

public record PatientPortalAccessLoginResponse(
        boolean authenticated,
        String message,
        String tenantId,
        String tenantCode,
        String patientDisplayName,
        String patientSessionToken
) {
}
