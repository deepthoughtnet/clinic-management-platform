package com.deepthoughtnet.clinic.api.patientportal.careai;

public record PatientPortalCareAiMessageRequest(
        String message,
        String language
) {
}
