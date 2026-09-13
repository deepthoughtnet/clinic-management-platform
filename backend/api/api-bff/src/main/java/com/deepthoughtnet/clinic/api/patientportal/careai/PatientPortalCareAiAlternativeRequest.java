package com.deepthoughtnet.clinic.api.patientportal.careai;

public record PatientPortalCareAiAlternativeRequest(
        boolean present,
        String target
) {
    public static PatientPortalCareAiAlternativeRequest none() {
        return new PatientPortalCareAiAlternativeRequest(false, null);
    }
}
