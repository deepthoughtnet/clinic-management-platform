package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiShadowContinuityTest {
    private final PatientPortalCareAiConversationStateReducer reducer = new PatientPortalCareAiConversationStateReducer();
    private final PatientPortalCareAiActionDecider decider = new PatientPortalCareAiActionDecider();

    @Test
    void dateCorrectionKeepsDoctorAndInvalidatesSlot() {
        PatientPortalCareAiBookingState state = pendingState();
        PatientPortalCareAiCanonicalTurn turn = turn(
                PatientPortalCareAiDialogAct.CHANGE_INFORMATION,
                PatientPortalCareAiConfirmationPolarity.NEGATIVE,
                "tomorrow", null, PatientPortalCareAiCorrection.none(), PatientPortalCareAiAlternativeRequest.none());
        PatientPortalCareAiResolvedTurnFacts facts = facts(null, "2026-09-10", null);

        PatientPortalCareAiBookingState next = reducer.reduce(state, turn, facts).state();

        assertThat(next.resolvedDoctor().name()).isEqualTo("Akshu");
        assertThat(next.preferredDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(next.selectedSlot()).isNull();
        assertThat(next.confirmationPending()).isFalse();
        assertThat(decider.decide(next, turn, facts, null).action())
                .isEqualTo(PatientPortalCareAiAction.CHECK_AVAILABILITY);
    }

    @Test
    void moreSlotsLeavesDoctorAuthoritativeAndChoosesAvailability() {
        PatientPortalCareAiBookingState state = pendingState();
        PatientPortalCareAiCanonicalTurn turn = turn(
                PatientPortalCareAiDialogAct.REQUEST_ALTERNATIVE,
                PatientPortalCareAiConfirmationPolarity.NONE, null, null,
                PatientPortalCareAiCorrection.none(), new PatientPortalCareAiAlternativeRequest(true, "slot"));
        PatientPortalCareAiResolvedTurnFacts facts = facts(null, null, null);

        PatientPortalCareAiBookingState next = reducer.reduce(state, turn, facts).state();

        assertThat(next.resolvedDoctor().name()).isEqualTo("Akshu");
        assertThat(decider.decide(next, turn, facts, null).action())
                .isEqualTo(PatientPortalCareAiAction.CHECK_AVAILABILITY);
    }

    @Test
    void positiveConfirmationUsesExistingStateWithoutResolvingDoctor() {
        PatientPortalCareAiBookingState state = pendingState();
        PatientPortalCareAiCanonicalTurn turn = turn(
                PatientPortalCareAiDialogAct.CONFIRM,
                PatientPortalCareAiConfirmationPolarity.POSITIVE, null, null,
                PatientPortalCareAiCorrection.none(), PatientPortalCareAiAlternativeRequest.none());

        PatientPortalCareAiActionDecision decision = decider.decide(
                state, turn, facts(null, null, null), null);

        assertThat(decision.action()).isEqualTo(PatientPortalCareAiAction.EXECUTE_PENDING_ACTION);
        assertThat(decision.skillId()).isEqualTo("appointment.book");
    }

    @Test
    void explicitExitWinsOverContextQuestionRouting() {
        PatientPortalCareAiBookingState state = pendingState();
        PatientPortalCareAiCanonicalTurn turn = new PatientPortalCareAiCanonicalTurn(
                PatientPortalCareAiDialogAct.ABANDON_WORKFLOW, PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                new PatientPortalCareAiCanonicalEntities(null, null, null, null, null, null, null, null, null),
                PatientPortalCareAiConfirmationPolarity.NONE, PatientPortalCareAiCorrection.none(),
                PatientPortalCareAiAlternativeRequest.none(), PatientPortalCareAiSelectionReference.none(),
                true, false, PatientPortalCareAiInterpretationSource.GEMINI, .95d);

        assertThat(decider.decide(state, turn, facts(null, null, null), null).action())
                .isEqualTo(PatientPortalCareAiAction.ABANDON_WORKFLOW);
    }

    private PatientPortalCareAiBookingState pendingState() {
        return new PatientPortalCareAiBookingState(
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING,
                new PatientPortalCareAiBookingCapability("d1", "Akshu", "c1", "t1", "ONLINE_BOOKING"),
                "General Medicine", LocalDate.of(2026, 9, 14), "afternoon", null,
                "2026-09-14@16:30",
                new PatientPortalCareAiCandidateContext("SLOT", 1, List.of("2026-09-14@16:30"), "d1", "c1", LocalDate.of(2026, 9, 14), "afternoon"),
                PatientPortalCareAiIntent.BOOK_APPOINTMENT, true, false,
                PatientPortalCareAiSkillOutcome.SUCCESS, 1);
    }

    private PatientPortalCareAiResolvedTurnFacts facts(String doctor, String date, String slot) {
        return new PatientPortalCareAiResolvedTurnFacts(
                doctor == null ? null : CanonicalResolution.resolved(doctor, List.of("d2"), List.of(), "test", 1, doctor),
                null, null,
                slot == null ? null : CanonicalResolution.resolved(slot, List.of(slot), List.of(), "test", 1, slot),
                date == null ? null : CanonicalResolution.resolved(date, List.of(), List.of(), "test", 1, date),
                null, null);
    }

    private PatientPortalCareAiCanonicalTurn turn(PatientPortalCareAiDialogAct act,
                                                   PatientPortalCareAiConfirmationPolarity confirmation,
                                                   String date, String slot,
                                                   PatientPortalCareAiCorrection correction,
                                                   PatientPortalCareAiAlternativeRequest alternative) {
        return new PatientPortalCareAiCanonicalTurn(
                act, PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                new PatientPortalCareAiCanonicalEntities(null, null, null, null, null, date, null, null, slot),
                confirmation, correction, alternative,
                new PatientPortalCareAiSelectionReference(slot != null, null, "slot"), false, false,
                PatientPortalCareAiInterpretationSource.GEMINI, .95d);
    }
}
