package com.deepthoughtnet.clinic.api.patientportal.careai;

/** Pure reducer output; no persistence or execution occurs here. */
public record PatientPortalCareAiStateTransition(
        PatientPortalCareAiBookingState state,
        boolean changed,
        boolean candidateContextInvalidated,
        String reason
) {
}
