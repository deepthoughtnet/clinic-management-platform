package com.deepthoughtnet.clinic.api.patientportal.careai;

public record PatientPortalCareAiCorrection(
        boolean present,
        String target
) {
    public static PatientPortalCareAiCorrection none() {
        return new PatientPortalCareAiCorrection(false, null);
    }
}
