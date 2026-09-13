package com.deepthoughtnet.clinic.api.patientportal.careai;

public record PatientPortalCareAiSelectionReference(
        boolean present,
        Integer ordinal,
        String target
) {
    public static PatientPortalCareAiSelectionReference none() {
        return new PatientPortalCareAiSelectionReference(false, null, null);
    }
}
