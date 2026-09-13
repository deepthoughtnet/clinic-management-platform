package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiConversationStateReducerTest {
    private final PatientPortalCareAiConversationStateReducer reducer =
            new PatientPortalCareAiConversationStateReducer();

    @Test
    void dateChangeInvalidatesSlotCandidatesAndSelectedSlot() {
        PatientPortalCareAiBookingState current = new PatientPortalCareAiBookingState(
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION,
                new PatientPortalCareAiBookingCapability("doctor-1", "Akshu", "clinic-1", "tenant-1", "ONLINE_BOOKING"),
                "GENERAL_MEDICINE",
                LocalDate.of(2026, 9, 10),
                "afternoon",
                null,
                "2026-09-10@16:30",
                new PatientPortalCareAiCandidateContext(
                        "SLOT", 3L, List.of("2026-09-10@16:30"), "doctor-1", "clinic-1",
                        LocalDate.of(2026, 9, 10), "afternoon"),
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                true,
                false,
                PatientPortalCareAiSkillOutcome.SUCCESS,
                3L
        );

        PatientPortalCareAiStateTransition transition = reducer.reduce(
                current,
                turn(new PatientPortalCareAiCanonicalEntities(
                        null, null, null, null, null, "2026-09-14", null, null, null),
                        PatientPortalCareAiDialogAct.CHANGE_INFORMATION,
                        PatientPortalCareAiConfirmationPolarity.NONE),
                null,
                null,
                LocalDate.of(2026, 9, 14),
                null,
                null,
                null
        );

        assertThat(transition.candidateContextInvalidated()).isTrue();
        assertThat(transition.state().preferredDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(transition.state().candidateContext()).isNull();
        assertThat(transition.state().selectedSlot()).isNull();
        assertThat(transition.state().confirmationPending()).isFalse();
    }

    @Test
    void positiveConfirmationPreservesPendingActionForExecution() {
        PatientPortalCareAiBookingState current = new PatientPortalCareAiBookingState(
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING,
                new PatientPortalCareAiBookingCapability("doctor-1", "Akshu", "clinic-1", "tenant-1", "ONLINE_BOOKING"),
                "GENERAL_MEDICINE",
                LocalDate.of(2026, 9, 14),
                "16:30",
                LocalTime.of(16, 30),
                "2026-09-14@16:30",
                null,
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                true,
                false,
                PatientPortalCareAiSkillOutcome.SUCCESS,
                4L
        );

        PatientPortalCareAiStateTransition transition = reducer.reduce(
                current,
                turn(emptyEntities(), PatientPortalCareAiDialogAct.CONFIRM,
                        PatientPortalCareAiConfirmationPolarity.POSITIVE),
                null, null, null, null, null, null
        );

        assertThat(transition.state().confirmationAccepted()).isTrue();
        assertThat(transition.state().pendingAction()).isEqualTo(PatientPortalCareAiIntent.BOOK_APPOINTMENT);
        assertThat(transition.state().selectedSlot()).isEqualTo("2026-09-14@16:30");
    }

    private PatientPortalCareAiCanonicalTurn turn(
            PatientPortalCareAiCanonicalEntities entities,
            PatientPortalCareAiDialogAct dialogAct,
            PatientPortalCareAiConfirmationPolarity confirmation
    ) {
        return new PatientPortalCareAiCanonicalTurn(
                dialogAct,
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                entities,
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

    private PatientPortalCareAiCanonicalEntities emptyEntities() {
        return new PatientPortalCareAiCanonicalEntities(null, null, null, null, null, null, null, null, null);
    }
}
