package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.LocalDate;
import java.time.LocalTime;

/** Immutable booking projection used by the controlled reducer migration. */
public record PatientPortalCareAiBookingState(
        PatientPortalCareAiIntent workflow,
        PatientPortalCareAiWorkflowSubState subState,
        PatientPortalCareAiBookingCapability resolvedDoctor,
        String resolvedSpeciality,
        LocalDate preferredDate,
        String preferredTimeWindow,
        LocalTime exactTime,
        String selectedSlot,
        PatientPortalCareAiCandidateContext candidateContext,
        PatientPortalCareAiIntent pendingAction,
        boolean confirmationPending,
        boolean confirmationAccepted,
        PatientPortalCareAiSkillOutcome lastExecutionResult,
        long conversationVersion
) {
    public static PatientPortalCareAiBookingState empty() {
        return new PatientPortalCareAiBookingState(
                null,
                PatientPortalCareAiWorkflowSubState.START,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                null,
                0L
        );
    }
}
