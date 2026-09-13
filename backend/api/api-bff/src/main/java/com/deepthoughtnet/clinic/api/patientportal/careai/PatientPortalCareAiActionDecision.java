package com.deepthoughtnet.clinic.api.patientportal.careai;

/** One deterministic next action for a canonical booking turn. */
public record PatientPortalCareAiActionDecision(
        PatientPortalCareAiAction action,
        String skillId,
        String reason,
        PatientPortalCareAiIntent pendingAction
) {
    public static PatientPortalCareAiActionDecision of(PatientPortalCareAiAction action, String reason) {
        return new PatientPortalCareAiActionDecision(action, null, reason, null);
    }
}
