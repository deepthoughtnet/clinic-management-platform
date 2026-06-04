package com.deepthoughtnet.clinic.api.patientportal.dto;

public record PatientPortalRegistrationResponse(
        boolean created,
        boolean linkedExistingPatient,
        String message,
        String tenantId,
        String patientDisplayName,
        String patientSessionToken
) {
}
