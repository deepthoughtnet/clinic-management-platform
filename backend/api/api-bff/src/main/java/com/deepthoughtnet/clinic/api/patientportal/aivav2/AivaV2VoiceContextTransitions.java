package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.PendingProviderSwitch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceClarificationKind;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceClarificationState;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceUnresolvedReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pure session transformations for guarded Voice context changes. */
final class AivaV2VoiceContextTransitions {
    private AivaV2VoiceContextTransitions() { }

    static SessionProjection requestProviderSwitch(SessionProjection session, ProviderCandidate target,
            UUID tenantId, String sourceContext, java.time.LocalDate explicitDate,
            String explicitTimeWindow, java.time.LocalTime explicitExactTime) {
        if (session == null || target == null || tenantId == null) return session;
        BookingDraft draft = session.activeDraft();
        ProviderCandidate current = draft != null && draft.selectedProvider() != null
                ? draft.selectedProvider() : session.activeVoiceDoctor();
        if (current == null || current.doctorId() == null || current.doctorId().equals(target.doctorId())) return session;
        AvailabilityResult availability = session.latestAvailabilityResult();
        java.time.LocalDate retainedDate = explicitDate != null ? explicitDate
                : draft != null && draft.preferredDate() != null ? draft.preferredDate()
                : availability == null ? null : availability.date();
        String retainedWindow = explicitTimeWindow != null ? explicitTimeWindow
                : draft != null && draft.preferredTimeWindow() != null ? draft.preferredTimeWindow()
                : availability == null ? null : availability.timeWindow();
        if (retainedWindow != null) retainedWindow = retainedWindow.toLowerCase(java.util.Locale.ROOT);
        java.time.LocalTime retainedExactTime = explicitExactTime != null ? explicitExactTime
                : draft == null || draft.selectedSlotReference() != null ? null : draft.exactTime();
        PendingProviderSwitch pending = new PendingProviderSwitch(tenantId, current.doctorId(), target.doctorId(),
                target.displayName(), draft == null ? null : draft.draftId(), draft == null ? 0 : draft.revision(),
                sourceContext == null ? session.activeWorkflow() : sourceContext,
                retainedDate, retainedWindow, retainedExactTime,
                availability == null ? null : availability.requestId(),
                availability == null ? 0 : availability.displayOffset(),
                draft == null ? null : draft.selectedSlotReference(),
                session.pendingConfirmation() == null ? null : session.pendingConfirmation().confirmationRef(),
                "USER_REQUESTED_PROVIDER_CHANGE");
        VoiceClarificationState clarification = new VoiceClarificationState(VoiceClarificationKind.INTENT, 0,
                VoiceUnresolvedReason.MISSING_REQUIRED_INFO, List.of("confirm_provider_switch", "decline_provider_switch"),
                false, "PROVIDER_SWITCH_CONFIRMATION", pending.originatingContext(), List.of(), false, pending);
        return session.withVoiceClarificationState(clarification);
    }

    static boolean providerSwitchStillBound(SessionProjection session, PendingProviderSwitch pending) {
        if (session == null || pending == null || !pending.tenantId().toString().equals(session.tenantId())) return false;
        BookingDraft draft = session.activeDraft();
        ProviderCandidate current = draft != null && draft.selectedProvider() != null
                ? draft.selectedProvider() : session.activeVoiceDoctor();
        if (current == null || !pending.currentProviderId().equals(current.doctorId())) return false;
        if (!java.util.Objects.equals(pending.originatingDraftId(), draft == null ? null : draft.draftId())
                || pending.originatingDraftRevision() != (draft == null ? 0 : draft.revision())) return false;
        UUID availabilityId = session.latestAvailabilityResult() == null ? null
                : session.latestAvailabilityResult().requestId();
        int displayOffset = session.latestAvailabilityResult() == null ? 0
                : session.latestAvailabilityResult().displayOffset();
        String selectedSlot = draft == null ? null : draft.selectedSlotReference();
        String confirmation = session.pendingConfirmation() == null ? null
                : session.pendingConfirmation().confirmationRef();
        return java.util.Objects.equals(pending.availabilityId(), availabilityId)
                && pending.displayOffset() == displayOffset
                && java.util.Objects.equals(pending.selectedSlotReference(), selectedSlot)
                && java.util.Objects.equals(pending.confirmationReference(), confirmation);
    }

    /** Retain user preferences while making all old-provider capabilities unusable. */
    static SessionProjection invalidateForProviderSwitch(SessionProjection session, UUID patientId,
            PendingProviderSwitch pending, UUID newDraftId, Instant now) {
        BookingDraft old = session.activeDraft();
        BookingDraft abandoned = old == null ? null : new BookingDraft(old.draftId(), old.patientSubjectId(),
                old.tenantScope(), old.selectedProvider(), old.specialtyFilter(), old.preferredDate(),
                old.preferredTimeWindow(), old.exactTime(), old.availabilityTimeConstraint(),
                old.latestAvailabilityRequestId(), old.selectedSlotReference(), DraftStatus.ABANDONED,
                old.revision(), old.expiresAt(), null);
        BookingDraft next = new BookingDraft(newDraftId, patientId,
                session.tenantId(), null, old == null ? null : old.specialtyFilter(),
                pending.requestedDate(), pending.requestedTimeWindow(), pending.requestedExactTime(),
                old == null ? null : old.availabilityTimeConstraint(), null, null, DraftStatus.COLLECTING,
                1L, now.plusSeconds(30 * 60), null);
        return new SessionProjection(session.conversationId(), session.patientId(), session.tenantId(), next,
                abandoned == null ? session.suspendedDraft() : abandoned, null, null, null,
                session.pendingCancellation(), session.pendingCancellationResolution(), session.pendingReschedule(),
                session.pendingRescheduleConfirmation(), null, session.closed(), session.version() + 1,
                session.expiresAt(), session.activeAppointmentReferent(), null, null,
                session.voiceAuthenticationState(), session.voiceGuestRegistrationState(), session.presentationMode(),
                session.completedBookingOutcome());
    }
}
