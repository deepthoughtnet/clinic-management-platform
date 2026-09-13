package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiActionDeciderTest {
    private final PatientPortalCareAiActionDecider decider = new PatientPortalCareAiActionDecider();

    @Test
    void positiveConfirmationExecutesPendingBookingWithoutLookup() {
        PatientPortalCareAiBookingState state = new PatientPortalCareAiBookingState(
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING,
                new PatientPortalCareAiBookingCapability("doctor-1", "Akshu", "clinic-1", "tenant-1", "ONLINE_BOOKING"),
                "GENERAL_MEDICINE",
                LocalDate.of(2026, 9, 14),
                "16:30",
                null,
                "2026-09-14@16:30",
                null,
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                true,
                false,
                PatientPortalCareAiSkillOutcome.SUCCESS,
                5L
        );

        PatientPortalCareAiActionDecision decision = decider.decide(
                state,
                canonical(PatientPortalCareAiDialogAct.CONFIRM, PatientPortalCareAiConfirmationPolarity.POSITIVE)
        );

        assertThat(decision.action()).isEqualTo(PatientPortalCareAiAction.EXECUTE_PENDING_ACTION);
        assertThat(decision.skillId()).isEqualTo("appointment.book");
        assertThat(decision.pendingAction()).isEqualTo(PatientPortalCareAiIntent.BOOK_APPOINTMENT);
    }

    @Test
    void dateOnlyAvailabilityRequestUsesProviderAvailabilityAction() {
        PatientPortalCareAiCanonicalTurn turn = new PatientPortalCareAiCanonicalTurn(
                PatientPortalCareAiDialogAct.START_REQUEST,
                PatientPortalCareAiIntent.FIND_DOCTOR,
                new PatientPortalCareAiCanonicalEntities(null, null, null, null, null, "tomorrow", "afternoon", null, null),
                PatientPortalCareAiConfirmationPolarity.NONE,
                PatientPortalCareAiCorrection.none(),
                PatientPortalCareAiAlternativeRequest.none(),
                PatientPortalCareAiSelectionReference.none(),
                false,
                false,
                PatientPortalCareAiInterpretationSource.GEMINI,
                0.94d
        );

        PatientPortalCareAiActionDecision decision = decider.decide(PatientPortalCareAiBookingState.empty(), turn);

        assertThat(decision.action()).isEqualTo(PatientPortalCareAiAction.FIND_PROVIDER_AVAILABILITY);
        assertThat(decision.skillId()).isEqualTo("doctor.find");
    }

    @Test
    void actionDecisionDependsOnCanonicalTurnNotRawTranscript() {
        PatientPortalCareAiCanonicalTurn first = canonical(
                PatientPortalCareAiDialogAct.PROVIDE_INFORMATION,
                PatientPortalCareAiConfirmationPolarity.NONE);
        PatientPortalCareAiCanonicalTurn equivalent = canonical(
                PatientPortalCareAiDialogAct.PROVIDE_INFORMATION,
                PatientPortalCareAiConfirmationPolarity.NONE);

        assertThat(decider.decide(PatientPortalCareAiBookingState.empty(), first))
                .isEqualTo(decider.decide(PatientPortalCareAiBookingState.empty(), equivalent));
    }

    private PatientPortalCareAiCanonicalTurn canonical(
            PatientPortalCareAiDialogAct dialogAct,
            PatientPortalCareAiConfirmationPolarity confirmation
    ) {
        return new PatientPortalCareAiCanonicalTurn(
                dialogAct,
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                new PatientPortalCareAiCanonicalEntities(null, null, null, null, null, null, null, null, null),
                confirmation,
                PatientPortalCareAiCorrection.none(),
                PatientPortalCareAiAlternativeRequest.none(),
                PatientPortalCareAiSelectionReference.none(),
                false,
                false,
                PatientPortalCareAiInterpretationSource.FAST_PATH,
                1.0d
        );
    }
}
