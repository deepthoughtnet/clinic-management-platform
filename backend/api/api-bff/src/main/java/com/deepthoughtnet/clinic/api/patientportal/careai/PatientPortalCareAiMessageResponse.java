package com.deepthoughtnet.clinic.api.patientportal.careai;

public record PatientPortalCareAiMessageResponse(
        String assistantMessage,
        PatientPortalCareAiStateResponse state
) {
}
