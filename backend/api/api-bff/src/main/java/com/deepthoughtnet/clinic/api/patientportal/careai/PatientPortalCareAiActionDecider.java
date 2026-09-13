package com.deepthoughtnet.clinic.api.patientportal.careai;

/** Computes the next booking action without inspecting raw transcript text. */
public final class PatientPortalCareAiActionDecider {
    public PatientPortalCareAiActionDecision decide(
            PatientPortalCareAiBookingState state,
            PatientPortalCareAiCanonicalTurn turn,
            PatientPortalCareAiResolvedTurnFacts facts,
            PatientPortalCareAiSkillResult<?> lastSkillResult
    ) {
        if (turn == null) return decide(state, (PatientPortalCareAiCanonicalTurn) null);
        if (state != null && turn.confirmation() == PatientPortalCareAiConfirmationPolarity.POSITIVE
                && state.subState() == PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING
                && state.pendingAction() == PatientPortalCareAiIntent.BOOK_APPOINTMENT
                && state.selectedSlot() != null) {
            return new PatientPortalCareAiActionDecision(
                    PatientPortalCareAiAction.EXECUTE_PENDING_ACTION,
                    "appointment.book", "positive-confirmation", PatientPortalCareAiIntent.BOOK_APPOINTMENT);
        }
        if (facts != null && facts.doctor() != null && facts.doctor().targeted()
                && facts.doctor().candidateText() != null
                && !facts.doctor().resolved()) {
            return PatientPortalCareAiActionDecision.of(
                    PatientPortalCareAiAction.ASK_CLARIFICATION,
                    "doctor-resolution-" + facts.doctor().status().name().toLowerCase());
        }
        if (facts != null && facts.speciality() != null && facts.speciality().targeted()
                && facts.speciality().candidateText() != null
                && !facts.speciality().resolved()) {
            return PatientPortalCareAiActionDecision.of(
                    PatientPortalCareAiAction.ASK_CLARIFICATION,
                    "speciality-resolution-" + facts.speciality().status().name().toLowerCase());
        }
        boolean dateOrTimeDelta = facts != null
                && ((facts.date() != null && facts.date().targeted())
                || (facts.timeWindow() != null && facts.timeWindow().targeted())
                || (facts.exactTime() != null && facts.exactTime().targeted()));
        if (state != null && state.resolvedDoctor() != null && dateOrTimeDelta) {
            return new PatientPortalCareAiActionDecision(
                    PatientPortalCareAiAction.CHECK_AVAILABILITY,
                    "availability.check", "resolved-doctor-with-temporal-delta", null);
        }
        return decide(state, turn);
    }

    public PatientPortalCareAiActionDecision decide(
            PatientPortalCareAiBookingState state,
            PatientPortalCareAiCanonicalTurn turn
    ) {
        if (turn == null) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.ASK_CLARIFICATION, "missing-canonical-turn");
        }
        if (turn.endConversation()) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.END_CONVERSATION, "canonical-end");
        }
        if (turn.abandonWorkflow()) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.ABANDON_WORKFLOW, "canonical-abandon");
        }
        if (state != null && turn.confirmation() == PatientPortalCareAiConfirmationPolarity.POSITIVE
                && state.confirmationPending()
                && state.pendingAction() != null) {
            return new PatientPortalCareAiActionDecision(
                    PatientPortalCareAiAction.EXECUTE_PENDING_ACTION,
                    skillFor(state.pendingAction()),
                    "positive-confirmation",
                    state.pendingAction()
            );
        }
        if (state != null && turn.confirmation() == PatientPortalCareAiConfirmationPolarity.NEGATIVE
                && state.confirmationPending()) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.REJECT_PENDING_ACTION, "negative-confirmation");
        }
        if (state != null && turn.dialogAct() == PatientPortalCareAiDialogAct.ASK_QUESTION
                && hasContextAnswer(state, turn)) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.ANSWER_CONTEXT, "context-question");
        }
        if (turn.selection().present()) {
            return PatientPortalCareAiActionDecision.of(
                    "slot".equalsIgnoreCase(turn.selection().target())
                            ? PatientPortalCareAiAction.SELECT_SLOT
                            : PatientPortalCareAiAction.SELECT_PROVIDER,
                    "canonical-selection"
            );
        }
        if (turn.alternative().present() && "doctor".equalsIgnoreCase(turn.alternative().target())) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.FIND_PROVIDER, "alternative-provider");
        }
        if (turn.alternative().present() && "slot".equalsIgnoreCase(turn.alternative().target())) {
            return PatientPortalCareAiActionDecision.of(
                    PatientPortalCareAiAction.CHECK_AVAILABILITY, "alternative-slot");
        }
        if (turn.intent() == PatientPortalCareAiIntent.FIND_DOCTOR
                && !hasText(turn.entities().doctor())
                && hasText(turn.entities().date())) {
            return new PatientPortalCareAiActionDecision(
                    PatientPortalCareAiAction.FIND_PROVIDER_AVAILABILITY,
                    "doctor.find",
                    "availability-first-provider-discovery",
                    null
            );
        }
        if (hasText(turn.entities().doctor()) || hasText(turn.entities().speciality())) {
            if (hasText(turn.entities().doctor()) && state != null && state.resolvedDoctor() == null) {
                return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.FIND_PROVIDER, "provider-candidate");
            }
            if (state != null && state.resolvedDoctor() != null && hasText(turn.entities().date())) {
                return new PatientPortalCareAiActionDecision(
                        PatientPortalCareAiAction.CHECK_AVAILABILITY,
                        "availability.check",
                        "resolved-booking-criteria",
                        null
                );
            }
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.FIND_PROVIDER, "speciality-provider-discovery");
        }
        if (state != null && state.confirmationPending() && state.selectedSlot() != null) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.ASK_CONFIRMATION, "confirmation-required");
        }
        if (state != null && state.candidateContext() != null
                && "SLOT".equalsIgnoreCase(state.candidateContext().type())) {
            return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.SHOW_SLOT_CHOICES, "slot-candidates-present");
        }
        return PatientPortalCareAiActionDecision.of(PatientPortalCareAiAction.ASK_MISSING_FIELD, "missing-booking-field");
    }

    private boolean hasContextAnswer(PatientPortalCareAiBookingState state, PatientPortalCareAiCanonicalTurn turn) {
        return state.preferredDate() != null
                || state.resolvedDoctor() != null
                || state.preferredTimeWindow() != null
                || state.exactTime() != null;
    }

    private String skillFor(PatientPortalCareAiIntent intent) {
        return switch (intent) {
            case BOOK_APPOINTMENT -> "appointment.book";
            case CANCEL_APPOINTMENT -> "appointment.cancel";
            case RESCHEDULE_APPOINTMENT -> "appointment.reschedule";
            default -> null;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
