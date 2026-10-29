package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;

/** Derived Voice collection contexts over the existing session and draft projections. */
final class AivaV2VoiceCollectionContext {
    enum State { NONE, AWAIT_PROVIDER, AWAIT_DATE, AWAIT_DAYPART }

    private AivaV2VoiceCollectionContext() { }

    static State state(SessionProjection session, Instant now) {
        if (!validCollectingBooking(session, now)) return State.NONE;
        BookingDraft draft = session.activeDraft();
        if (provider(draft, session) == null) return State.AWAIT_PROVIDER;
        if (draft.preferredDate() == null) return State.AWAIT_DATE;
        if (draft.selectedSlotReference() == null && draft.preferredTimeWindow() == null
                && draft.exactTime() == null && draft.availabilityTimeConstraint() == null
                && hasMultipleDayparts(session, draft, now)) return State.AWAIT_DAYPART;
        return State.NONE;
    }

    static boolean isAwaitProvider(SessionProjection session, Instant now) {
        return state(session, now) == State.AWAIT_PROVIDER;
    }

    static boolean isAwaitDate(SessionProjection session, Instant now) {
        return state(session, now) == State.AWAIT_DATE;
    }

    static boolean isAwaitDaypart(SessionProjection session, Instant now) {
        return state(session, now) == State.AWAIT_DAYPART;
    }

    static boolean validCollectingBooking(SessionProjection session, Instant now) {
        if (session == null || session.closed() || !"BOOKING".equals(session.activeWorkflow())
                || session.activeDraft() == null || session.activeDraft().status() != DraftStatus.COLLECTING
                || session.pendingConfirmation() != null || session.voiceClarificationState() != null
                || session.pendingTemporalClarification() != null || session.pendingCancellation() != null
                || session.pendingCancellationResolution() != null || session.pendingReschedule() != null
                || session.pendingRescheduleConfirmation() != null) return false;
        BookingDraft draft = session.activeDraft();
        return draft.draftId() != null && draft.patientSubjectId() != null
                && draft.patientSubjectId().equals(session.patientId())
                && draft.tenantScope() != null && draft.tenantScope().equals(session.tenantId())
                && draft.expiresAt() != null && now != null && draft.expiresAt().isAfter(now);
    }

    static ProviderCandidate provider(BookingDraft draft, SessionProjection session) {
        ProviderCandidate provider = draft == null ? null : draft.selectedProvider();
        if (provider == null && session != null) provider = session.activeVoiceDoctor();
        return provider != null && provider.source() == ProviderSource.CARE_PRIVATE
                && provider.doctorId() != null && provider.clinicId() != null
                && provider.providerHandle() != null ? provider : null;
    }

    static boolean hasMultipleDayparts(SessionProjection session, BookingDraft draft, Instant now) {
        return availableDayparts(session, draft, now).size() > 1;
    }

    static List<NormalizedUserTurn.Daypart> availableDayparts(SessionProjection session,
            BookingDraft draft, Instant now) {
        AvailabilityResult availability = session == null ? null : session.latestAvailabilityResult();
        if (!isBoundAvailability(session, draft, availability, now) || availability.slots().isEmpty()) return List.of();
        Set<NormalizedUserTurn.Daypart> dayparts = EnumSet.noneOf(NormalizedUserTurn.Daypart.class);
        availability.slots().stream().map(slot -> slot.startsAt()).filter(Objects::nonNull)
                .map(AivaV2VoiceCollectionContext::daypart).forEach(dayparts::add);
        return List.copyOf(dayparts);
    }

    static boolean isBoundAvailability(SessionProjection session, BookingDraft draft,
            AvailabilityResult availability, Instant now) {
        ProviderCandidate provider = provider(draft, session);
        return session != null && draft != null && availability != null && now != null
                && provider != null && draft.preferredDate() != null
                && draft.latestAvailabilityRequestId() != null
                && Objects.equals(draft.latestAvailabilityRequestId(), availability.requestId())
                && Objects.equals(draft.draftId(), availability.draftId())
                && draft.revision() == availability.draftRevision()
                && Objects.equals(draft.preferredDate(), availability.date())
                && Objects.equals(provider.doctorId(), availability.doctorId())
                && Objects.equals(provider.clinicId(), availability.clinicId())
                && Objects.equals(provider.providerHandle(), availability.providerHandle())
                && availability.expiresAt() != null && availability.expiresAt().isAfter(now);
    }

    private static NormalizedUserTurn.Daypart daypart(LocalTime time) {
        return time.isBefore(LocalTime.NOON) ? NormalizedUserTurn.Daypart.MORNING
                : time.isBefore(LocalTime.of(17, 0)) ? NormalizedUserTurn.Daypart.AFTERNOON
                : NormalizedUserTurn.Daypart.EVENING;
    }
}
