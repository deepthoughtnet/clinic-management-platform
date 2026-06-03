package com.deepthoughtnet.clinic.api.patientportal.careai;

public record PatientPortalCareAiResetResponse(
        boolean cleared,
        String message
) {
}
