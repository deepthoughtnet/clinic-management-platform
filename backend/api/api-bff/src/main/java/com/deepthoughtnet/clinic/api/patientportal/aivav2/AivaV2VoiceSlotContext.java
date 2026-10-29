package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import java.time.Instant;
import java.util.Objects;

/** Shared predicate for the Voice-only logical slot-selection boundary. */
final class AivaV2VoiceSlotContext {
    private AivaV2VoiceSlotContext() { }

    static boolean isAwaitSlot(SessionProjection session, Instant now) {
        if (session == null || session.closed() || !"BOOKING".equals(session.activeWorkflow())
                || session.pendingConfirmation() != null) return false;
        BookingDraft draft = session.activeDraft();
        AvailabilityResult availability = session.latestAvailabilityResult();
        if (draft == null || draft.status() != DraftStatus.COLLECTING || draft.selectedProvider() == null
                || draft.selectedProvider().source() != ProviderSource.CARE_PRIVATE
                || draft.selectedProvider().doctorId() == null || draft.preferredDate() == null
                || draft.selectedSlotReference() != null || draft.patientSubjectId() == null
                || draft.tenantScope() == null || draft.latestAvailabilityRequestId() == null
                || draft.expiresAt() == null || !draft.expiresAt().isAfter(now)
                || availability == null || availability.requestId() == null || availability.draftId() == null
                || availability.expiresAt() == null || !availability.expiresAt().isAfter(now)
                || availability.slots().isEmpty() || availability.pageSize() <= 0
                || availability.displayOffset() >= availability.slots().size()
                || availability.displayOffset() + availability.pageSize() <= availability.displayOffset()) return false;
        return Objects.equals(draft.draftId(), availability.draftId())
                && draft.revision() == availability.draftRevision()
                && Objects.equals(draft.latestAvailabilityRequestId(), availability.requestId())
                && Objects.equals(draft.preferredDate(), availability.date())
                && Objects.equals(draft.selectedProvider().doctorId(), availability.doctorId())
                && Objects.equals(draft.selectedProvider().clinicId(), availability.clinicId())
                && Objects.equals(draft.selectedProvider().providerHandle(), availability.providerHandle())
                && !"AIVA_BOOKING_PENDING_DAYPART".equals(availability.cursor());
    }

    static boolean hasUndisplayedSlots(AvailabilityResult availability) {
        return availability != null
                && availability.displayOffset() + availability.pageSize() < availability.slots().size();
    }

    static boolean explicitlySearchesNextDate(String rawText) {
        String raw = rawText == null ? "" : rawText.toLowerCase(java.util.Locale.ROOT);
        return raw.matches("(?s).*(?:next\\s+(?:available\\s+)?date|another\\s+date|future\\s+date|"
                + "अगली\\s+(?:available\\s+)?तारीख|दूसरी\\s+तारीख|अगली\\s+तारीख|"
                + "kal\\s+nahi\\s+to\\s+next\\s+date|इस\\s+दिन\\s+नहीं.{0,20}अगली\\s+available\\s+date).*" );
    }
}
