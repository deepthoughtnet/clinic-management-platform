package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Pure booking-state reducer used first in shadow mode. It accepts only
 * CanonicalTurn data and already-resolved domain facts.
 */
public final class PatientPortalCareAiConversationStateReducer {
    public PatientPortalCareAiStateTransition reduce(
            PatientPortalCareAiBookingState current,
            PatientPortalCareAiCanonicalTurn turn,
            PatientPortalCareAiResolvedTurnFacts facts
    ) {
        PatientPortalCareAiResolvedTurnFacts safeFacts = facts == null
                ? new PatientPortalCareAiResolvedTurnFacts(
                PatientPortalCareAiTurnFactDelta.unchanged(), PatientPortalCareAiTurnFactDelta.unchanged(),
                PatientPortalCareAiTurnFactDelta.unchanged(), PatientPortalCareAiTurnFactDelta.unchanged(),
                PatientPortalCareAiTurnFactDelta.unchanged(), PatientPortalCareAiTurnFactDelta.unchanged(),
                PatientPortalCareAiTurnFactDelta.unchanged())
                : facts;
        PatientPortalCareAiBookingCapability doctor = safeFacts.resolvedDoctor();
        PatientPortalCareAiBookingState existing = current == null ? PatientPortalCareAiBookingState.empty() : current;
        if (doctor != null && safeFacts.doctor().candidateIds().size() == 1) {
            doctor = new PatientPortalCareAiBookingCapability(
                    doctor.id(), doctor.name(),
                    existing.resolvedDoctor() == null ? null : existing.resolvedDoctor().clinicId(),
                    existing.resolvedDoctor() == null ? null : existing.resolvedDoctor().tenantId(),
                    existing.resolvedDoctor() == null ? null : existing.resolvedDoctor().bookingMode());
        }
        if (safeFacts.doctor().mutation() == PatientPortalCareAiFactMutation.CLEAR) doctor = null;
        return reduce(current, turn, doctor,
                safeFacts.speciality() != null && safeFacts.speciality().resolved() ? safeFacts.speciality().canonicalValue() : null,
                safeFacts.resolvedDate(), safeFacts.resolvedTimeWindow(), safeFacts.resolvedExactTime(), safeFacts.resolvedSlot());
    }

    public PatientPortalCareAiStateTransition reduce(
            PatientPortalCareAiBookingState current,
            PatientPortalCareAiCanonicalTurn turn,
            PatientPortalCareAiBookingCapability resolvedDoctor,
            String resolvedSpeciality,
            LocalDate resolvedDate,
            String resolvedTimeWindow,
            LocalTime resolvedExactTime,
            String resolvedSlot
    ) {
        PatientPortalCareAiBookingState state = current == null
                ? PatientPortalCareAiBookingState.empty()
                : current;
        if (turn == null) {
            return new PatientPortalCareAiStateTransition(state, false, false, "no-turn");
        }

        if (turn.endConversation() || turn.abandonWorkflow()) {
            PatientPortalCareAiBookingState abandoned = new PatientPortalCareAiBookingState(
                    null,
                    PatientPortalCareAiWorkflowSubState.CANCELLED,
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
                    state.conversationVersion() + 1
            );
            return transition(abandoned, true, true, "conversation-control");
        }

        PatientPortalCareAiCanonicalEntities entities = turn.entities();
        boolean criteriaChanged = resolvedDoctor != null
                || resolvedSpeciality != null
                || resolvedDate != null
                || resolvedTimeWindow != null
                || resolvedExactTime != null
                || turn.correction().present()
                || turn.alternative().present();
        PatientPortalCareAiCandidateContext candidateContext = criteriaChanged ? null : state.candidateContext();
        String selectedSlot = criteriaChanged ? null : state.selectedSlot();
        boolean confirmationPending = criteriaChanged ? false : state.confirmationPending();
        PatientPortalCareAiIntent pendingAction = criteriaChanged ? null : state.pendingAction();
        boolean confirmationAccepted = false;

        PatientPortalCareAiBookingCapability nextDoctor = resolvedDoctor == null
                ? state.resolvedDoctor()
                : resolvedDoctor;
        if (turn.correction().present() && "doctor".equalsIgnoreCase(turn.correction().target()) && resolvedDoctor == null) {
            nextDoctor = null;
        }
        if (turn.alternative().present() && "doctor".equalsIgnoreCase(turn.alternative().target()) && resolvedDoctor == null) {
            nextDoctor = null;
        }
        String nextSpeciality = resolvedSpeciality == null
                ? state.resolvedSpeciality()
                : resolvedSpeciality;
        LocalDate nextDate = resolvedDate == null ? state.preferredDate() : resolvedDate;
        String nextTimeWindow = resolvedTimeWindow == null ? state.preferredTimeWindow() : resolvedTimeWindow;
        LocalTime nextExactTime = resolvedExactTime == null ? state.exactTime() : resolvedExactTime;

        if (resolvedSlot != null) {
            selectedSlot = resolvedSlot;
            confirmationPending = true;
            pendingAction = PatientPortalCareAiIntent.BOOK_APPOINTMENT;
        }
        if (turn.confirmation() == PatientPortalCareAiConfirmationPolarity.POSITIVE
                && state.confirmationPending()
                && state.pendingAction() != null
                && resolvedSlot == null
                && !criteriaChanged) {
            confirmationAccepted = true;
        } else if (turn.confirmation() == PatientPortalCareAiConfirmationPolarity.NEGATIVE) {
            confirmationPending = false;
            pendingAction = null;
        }

        PatientPortalCareAiWorkflowSubState nextSubState = nextSubState(
                state,
                turn,
                nextDoctor,
                nextDate,
                nextTimeWindow,
                selectedSlot,
                confirmationPending
        );
        PatientPortalCareAiBookingState next = new PatientPortalCareAiBookingState(
                state.workflow() == null ? normalizedIntent(turn.intent()) : state.workflow(),
                nextSubState,
                nextDoctor,
                nextSpeciality,
                nextDate,
                nextTimeWindow,
                nextExactTime,
                selectedSlot,
                candidateContext,
                pendingAction,
                confirmationPending,
                confirmationAccepted,
                state.lastExecutionResult(),
                state.conversationVersion() + (criteriaChanged || confirmationAccepted ? 1 : 0)
        );
        return transition(next, !next.equals(state), criteriaChanged, criteriaChanged ? "criteria-change" : "turn-applied");
    }

    public PatientPortalCareAiStateTransition reduceSkillResult(
            PatientPortalCareAiBookingState current,
            PatientPortalCareAiSkillOutcome outcome,
            PatientPortalCareAiCandidateContext candidateContext,
            String selectedSlot
    ) {
        PatientPortalCareAiBookingState state = current == null
                ? PatientPortalCareAiBookingState.empty()
                : current;
        PatientPortalCareAiWorkflowSubState subState = state.subState();
        boolean confirmationPending = state.confirmationPending();
        PatientPortalCareAiIntent pendingAction = state.pendingAction();
        if (outcome == PatientPortalCareAiSkillOutcome.SUCCESS
                || outcome == PatientPortalCareAiSkillOutcome.MULTIPLE_MATCHES) {
            if (selectedSlot != null) {
                confirmationPending = true;
                pendingAction = PatientPortalCareAiIntent.BOOK_APPOINTMENT;
                subState = PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING;
            } else if (candidateContext != null && "SLOT".equals(candidateContext.type())) {
                subState = PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION;
            } else if (candidateContext != null) {
                subState = PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION;
            }
        } else if (outcome == PatientPortalCareAiSkillOutcome.NO_MATCH) {
            subState = state.resolvedDoctor() == null
                    ? PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY
                    : PatientPortalCareAiWorkflowSubState.NEED_DATE;
            confirmationPending = false;
            pendingAction = null;
        } else if (outcome != null) {
            subState = PatientPortalCareAiWorkflowSubState.FAILED;
        }
        PatientPortalCareAiBookingState next = new PatientPortalCareAiBookingState(
                state.workflow(), subState, state.resolvedDoctor(), state.resolvedSpeciality(),
                state.preferredDate(), state.preferredTimeWindow(), state.exactTime(),
                selectedSlot == null ? state.selectedSlot() : selectedSlot,
                candidateContext, pendingAction, confirmationPending, false, outcome,
                state.conversationVersion()
        );
        return transition(next, !next.equals(state), false, "skill-result:" + outcome);
    }

    public PatientPortalCareAiStateTransition reduceSkillResult(
            PatientPortalCareAiBookingState current,
            String skillId,
            PatientPortalCareAiSkillResult<?> result,
            PatientPortalCareAiCandidateContext candidateContext,
            String selectedSlot
    ) {
        if (result == null) {
            return new PatientPortalCareAiStateTransition(
                    current == null ? PatientPortalCareAiBookingState.empty() : current,
                    false, false, "no-skill-result");
        }
        PatientPortalCareAiBookingState state = current == null
                ? PatientPortalCareAiBookingState.empty() : current;
        if ("appointment.book".equals(skillId)
                && result.outcome() == PatientPortalCareAiSkillOutcome.SUCCESS) {
            PatientPortalCareAiBookingState completed = new PatientPortalCareAiBookingState(
                    state.workflow(), PatientPortalCareAiWorkflowSubState.COMPLETED,
                    state.resolvedDoctor(), state.resolvedSpeciality(), state.preferredDate(),
                    state.preferredTimeWindow(), state.exactTime(), state.selectedSlot(),
                    null, null, false, false, result.outcome(), state.conversationVersion());
            return transition(completed, !completed.equals(state), false, "skill-result:appointment.book:success");
        }
        if (result.outcome() == PatientPortalCareAiSkillOutcome.RUNNING) {
            PatientPortalCareAiBookingState waiting = new PatientPortalCareAiBookingState(
                    state.workflow(), PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL,
                    state.resolvedDoctor(), state.resolvedSpeciality(), state.preferredDate(),
                    state.preferredTimeWindow(), state.exactTime(), state.selectedSlot(),
                    state.candidateContext(), state.pendingAction(), state.confirmationPending(),
                    false, result.outcome(), state.conversationVersion());
            return transition(waiting, !waiting.equals(state), false, "skill-result:running");
        }
        return reduceSkillResult(current, result.outcome(), candidateContext, selectedSlot);
    }

    private PatientPortalCareAiWorkflowSubState nextSubState(
            PatientPortalCareAiBookingState state,
            PatientPortalCareAiCanonicalTurn turn,
            PatientPortalCareAiBookingCapability doctor,
            LocalDate date,
            String timeWindow,
            String selectedSlot,
            boolean confirmationPending
    ) {
        if (confirmationPending && selectedSlot != null) {
            return PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING;
        }
        if (turn.selection().present() && state.candidateContext() != null) {
            return PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION;
        }
        if (doctor == null && !hasSpeciality(turn, state)) {
            return PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY;
        }
        if (doctor == null) {
            return PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION;
        }
        if (date == null) {
            return PatientPortalCareAiWorkflowSubState.NEED_DATE;
        }
        if (timeWindow == null && state.exactTime() == null) {
            return PatientPortalCareAiWorkflowSubState.NEED_DATE;
        }
        return PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY;
    }

    private boolean hasSpeciality(PatientPortalCareAiCanonicalTurn turn, PatientPortalCareAiBookingState state) {
        return turn.entities().speciality() != null || state.resolvedSpeciality() != null;
    }

    private PatientPortalCareAiIntent normalizedIntent(PatientPortalCareAiIntent intent) {
        return PatientPortalCareAiIntent.normalize(intent);
    }

    private PatientPortalCareAiStateTransition transition(
            PatientPortalCareAiBookingState state,
            boolean changed,
            boolean invalidated,
            String reason
    ) {
        return new PatientPortalCareAiStateTransition(state, changed, invalidated, reason);
    }
}
