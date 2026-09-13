package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiShadowCertificationTest {
    private final PatientPortalCareAiConversationStateReducer reducer = new PatientPortalCareAiConversationStateReducer();
    private final PatientPortalCareAiActionDecider decider = new PatientPortalCareAiActionDecider();

    @Test
    void certifiesEightShadowBookingScenariosWithoutExecutingWrites() {
        PatientPortalCareAiBookingState book = PatientPortalCareAiBookingState.empty();
        PatientPortalCareAiResolvedTurnFacts akshuTomorrow = facts("d-akshu", "Akshu", "2026-09-10", null, null);
        PatientPortalCareAiCanonicalTurn bookTurn = turn(PatientPortalCareAiDialogAct.START_REQUEST,
                PatientPortalCareAiIntent.BOOK_APPOINTMENT, "Akshu", "tomorrow", null, false, false,
                PatientPortalCareAiConfirmationPolarity.NONE);
        PatientPortalCareAiStateTransition bookedCriteria = reducer.reduce(book, bookTurn, akshuTomorrow);
        assertThat(decider.decide(bookedCriteria.state(), bookTurn, akshuTomorrow, null).action())
                .isEqualTo(PatientPortalCareAiAction.CHECK_AVAILABILITY); // 1

        PatientPortalCareAiResolvedTurnFacts monday = facts("d-akshu", "Akshu", "2026-09-14", null, null);
        PatientPortalCareAiCanonicalTurn changeDate = turn(PatientPortalCareAiDialogAct.CHANGE_INFORMATION,
                PatientPortalCareAiIntent.BOOK_APPOINTMENT, null, "next Monday", null, false, false,
                PatientPortalCareAiConfirmationPolarity.NONE);
        assertThat(reducer.reduce(bookedCriteria.state(), changeDate, monday).state().preferredDate())
                .isEqualTo(LocalDate.of(2026, 9, 14)); // 2

        PatientPortalCareAiResolvedTurnFacts otherDoctor = facts("d-uat", "UAT Automation", "2026-09-14", null, null);
        assertThat(otherDoctor.doctor().status()).isEqualTo(CanonicalResolutionStatus.RESOLVED); // 3

        PatientPortalCareAiCanonicalTurn availability = turn(PatientPortalCareAiDialogAct.START_REQUEST,
                PatientPortalCareAiIntent.FIND_DOCTOR, null, "tomorrow", null, false, false,
                PatientPortalCareAiConfirmationPolarity.NONE);
        assertThat(decider.decide(PatientPortalCareAiBookingState.empty(), availability, facts(null, null, "2026-09-10", null, null), null).action())
                .isEqualTo(PatientPortalCareAiAction.FIND_PROVIDER_AVAILABILITY); // 4

        PatientPortalCareAiBookingState withContext = new PatientPortalCareAiBookingState(
                PatientPortalCareAiIntent.BOOK_APPOINTMENT, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION,
                new PatientPortalCareAiBookingCapability("d-akshu", "Akshu", "c1", "t1", "ONLINE_BOOKING"),
                null, LocalDate.of(2026, 9, 10), "afternoon", null, null, null, null, false, false, null, 1);
        PatientPortalCareAiCanonicalTurn question = turn(PatientPortalCareAiDialogAct.ASK_QUESTION,
                PatientPortalCareAiIntent.BOOK_APPOINTMENT, null, null, null, false, false,
                PatientPortalCareAiConfirmationPolarity.NONE);
        assertThat(decider.decide(withContext, question, facts(null, null, null, null, null), null).action())
                .isEqualTo(PatientPortalCareAiAction.ANSWER_CONTEXT); // 5

        PatientPortalCareAiCanonicalTurn select = turn(PatientPortalCareAiDialogAct.SELECT_OPTION,
                PatientPortalCareAiIntent.BOOK_APPOINTMENT, null, null, "4:30", false, false,
                PatientPortalCareAiConfirmationPolarity.NONE);
        assertThat(decider.decide(withContext, select, facts(null, null, null, null, "4:30"), null).action())
                .isEqualTo(PatientPortalCareAiAction.SELECT_SLOT); // 6

        PatientPortalCareAiBookingState pending = new PatientPortalCareAiBookingState(
                PatientPortalCareAiIntent.BOOK_APPOINTMENT, PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING,
                withContext.resolvedDoctor(), null, withContext.preferredDate(), withContext.preferredTimeWindow(), null,
                "2026-09-10@16:30", null, PatientPortalCareAiIntent.BOOK_APPOINTMENT, true, false, null, 2);
        assertThat(decider.decide(pending, turn(PatientPortalCareAiDialogAct.CONFIRM,
                PatientPortalCareAiIntent.BOOK_APPOINTMENT, null, null, null, false, false,
                PatientPortalCareAiConfirmationPolarity.POSITIVE), facts(null, null, null, null, null), null).action())
                .isEqualTo(PatientPortalCareAiAction.EXECUTE_PENDING_ACTION); // 7
        assertThat(decider.decide(pending, turn(PatientPortalCareAiDialogAct.CONFIRM,
                PatientPortalCareAiIntent.BOOK_APPOINTMENT, null, null, null, false, false,
                PatientPortalCareAiConfirmationPolarity.POSITIVE), facts(null, null, null, null, null), null).skillId())
                .isEqualTo("appointment.book"); // 8: Hindi confirmation has the same canonical polarity.
    }

    private PatientPortalCareAiResolvedTurnFacts facts(String doctorId, String doctorName, String date,
                                                        String timeWindow, String selection) {
        CanonicalResolution doctor = doctorId == null
                ? CanonicalResolution.unresolved(List.of(), "not-provided", 0, null)
                : CanonicalResolution.resolved(doctorName, List.of(doctorId), List.of(), "test", 1, doctorName);
        CanonicalResolution dateResolution = date == null
                ? CanonicalResolution.unresolved(List.of(), "not-provided", 0, null)
                : CanonicalResolution.resolved(date, List.of(), List.of(), "test", 1, date);
        CanonicalResolution selected = selection == null
                ? CanonicalResolution.unresolved(List.of(), "not-provided", 0, null)
                : CanonicalResolution.resolved(selection, List.of(selection), List.of(), "test", 1, selection);
        return new PatientPortalCareAiResolvedTurnFacts(doctor,
                CanonicalResolution.unresolved(List.of(), "not-provided", 0, null),
                CanonicalResolution.unresolved(List.of(), "not-provided", 0, null), selected,
                dateResolution,
                timeWindow == null ? CanonicalResolution.unresolved(List.of(), "not-provided", 0, null)
                        : CanonicalResolution.resolved(timeWindow, List.of(), List.of(), "test", 1, timeWindow),
                CanonicalResolution.unresolved(List.of(), "not-provided", 0, null));
    }

    private PatientPortalCareAiCanonicalTurn turn(PatientPortalCareAiDialogAct act, PatientPortalCareAiIntent intent,
                                                   String doctor, String date, String selection, boolean abandon,
                                                   boolean end, PatientPortalCareAiConfirmationPolarity confirmation) {
        return new PatientPortalCareAiCanonicalTurn(act, intent,
                new PatientPortalCareAiCanonicalEntities(doctor, null, null, null, null, date, null, null, selection),
                confirmation, PatientPortalCareAiCorrection.none(), PatientPortalCareAiAlternativeRequest.none(),
                new PatientPortalCareAiSelectionReference(selection != null, selection == null ? null : 1, "slot"),
                abandon, end, PatientPortalCareAiInterpretationSource.FAST_PATH, 1);
    }
}
