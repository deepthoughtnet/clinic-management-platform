package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.booking.BookingExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.booking.VoiceBookingExecutionService;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Logical Voice confirmation boundary derived from the existing prepared draft and confirmation binding. */
final class AivaV2VoiceConfirmationContext {
    private static final Pattern EXPLICIT_POSITIVE = Pattern.compile("(?iu)^\\s*(?:"
            + "yes(?:\\s+please)?(?:\\s+book(?:\\s+it)?(?:\\s+kar\\s+do)?)?|"
            + "yes\\s+book(?:\\s+it)?(?:\\s+kar\\s+do)?|book\\s+it(?:\\s+please)?|confirm(?:\\s+it)?|go\\s+ahead|"
            + "haan(?:\\s+ji)?(?:\\s+(?:book(?:\\s+it)?(?:\\s+kar\\s+(?:do|dijiye))?|kar\\s+(?:do|o|dijiye)))?|"
            + "ha(?:an)?(?:\\s+ji)?(?:\\s+(?:book(?:\\s+it)?(?:\\s+kar\\s+(?:do|dijiye))?|kar\\s+(?:do|o|dijiye)))?|"
            + "confirm\\s+kar\\s+do|haan\\s+book\\s+karo|ha(?:an)?\\s+book\\s+karo|"
            + "yes\\s+book\\s+kar\\s+do|book\\s+kar\\s+(?:do|dijiye|dijiye)|"
            + "हाँ(?:\\s*जी)?(?:\\s+(?:बुक(?:\\s+कर\\s+(?:दो|दीजिए|दीजिये))?|कर\\s+(?:दो|दीजिए|दीजिये|दीजिएगा|तो)))?|"
            + "हां(?:\\s*जी)?(?:\\s+(?:बुक(?:\\s+कर\\s+(?:दो|दीजिए|दीजिये))?|कर\\s+(?:दो|दीजिए|दीजिये|दीजिएगा|तो)))?|"
            + "जी\\s+हाँ|जी\\s+हां|पक्का\\s+कर\\s+दो|पक्का\\s+कर\\s+दीजिए|"
            + "ठीक है कर दो|ठीक है बुक कर दो|बुक कर दो|बुक कर दीजिए|बुक कर दीजिये|कर दो|कर दीजिए|कर दीजिये)\\s*[.!?।]*$");
    private static final Pattern EXPLICIT_NEGATIVE = Pattern.compile("(?iu)^\\s*(?:no(?:\\s+please)?|"
            + "nahi(?:\\s+ji)?|nahin(?:\\s+ji)?|mat karo|abhi nahi|"
            + "नहीं(?:\\s*जी)?|नही(?:\\s*जी)?|मत करो|अभी नहीं)\\s*[.!?।]*$");

    private AivaV2VoiceConfirmationContext() { }

    static boolean isAwaitConfirmation(SessionProjection session, BookingExecutionContext execution,
            VoiceBookingExecutionService bookingExecution, Instant now) {
        if (session == null || execution == null || bookingExecution == null || now == null || session.closed()
                || !"BOOKING_CONFIRMATION".equals(session.activeWorkflow())
                || !execution.authorizationValidated()
                || execution.allowedOperation() != BookingExecutionContext.Operation.BOOK_APPOINTMENT
                || !Objects.equals(session.tenantId(), execution.tenantId().toString())
                || !Objects.equals(session.patientId(), execution.patientId())
                || !Objects.equals(session.conversationId(), execution.providerCallId())
                || !Objects.equals(execution.sessionId(), session.conversationId())
                || session.expiresAt() == null || !session.expiresAt().isAfter(now)) return false;

        BookingDraft draft = session.activeDraft();
        BookingConfirmation pending = session.pendingConfirmation();
        AvailabilityResult availability = session.latestAvailabilityResult();
        ProviderCandidate provider = draft == null ? null : draft.selectedProvider();
        if (draft == null || (draft.status() != DraftStatus.PREPARED
                && draft.status() != DraftStatus.READY_FOR_CONFIRMATION)
                || draft.draftId() == null || draft.patientSubjectId() == null
                || !draft.patientSubjectId().equals(session.patientId())
                || !Objects.equals(draft.tenantScope(), session.tenantId())
                || draft.expiresAt() == null || !draft.expiresAt().isAfter(now)
                || provider == null || (provider.source() != ProviderSource.CARE_PRIVATE
                        && provider.source() != ProviderSource.DISCOVER_PUBLIC)
                || provider.doctorId() == null || provider.clinicId() == null
                || provider.providerHandle() == null || !session.tenantId().equals(provider.tenantId())
                || draft.preferredDate() == null || draft.exactTime() == null
                || draft.selectedSlotReference() == null || draft.latestAvailabilityRequestId() == null
                || availability == null || availability.expiresAt() == null || !availability.expiresAt().isAfter(now)
                || !draft.draftId().equals(availability.draftId())
                || draft.revision() != availability.draftRevision()
                || !draft.latestAvailabilityRequestId().equals(availability.requestId())
                || !draft.preferredDate().equals(availability.date())
                || !Objects.equals(provider.doctorId(), availability.doctorId())
                || !Objects.equals(provider.clinicId(), availability.clinicId())
                || !Objects.equals(provider.providerHandle(), availability.providerHandle())
                || availability.slots().stream().noneMatch(slot -> draft.selectedSlotReference().equals(slot.slotReference())
                        && draft.exactTime().equals(slot.startsAt()))) return false;

        if (pending == null || pending.confirmationRef() == null || pending.expiresAt() == null
                || !pending.expiresAt().isAfter(now)
                || !Objects.equals(pending.idempotencyKey(), pending.confirmationRef())
                || !Objects.equals(pending.draftId(), draft.draftId())
                || pending.draftRevision() != draft.revision()
                || !Objects.equals(pending.providerHandle(), provider.providerHandle())
                || !Objects.equals(pending.doctorId(), provider.doctorId())
                || !Objects.equals(pending.clinicId(), provider.clinicId())
                || !Objects.equals(pending.availabilityRequestId(), availability.requestId())
                || !Objects.equals(pending.slotReference(), draft.selectedSlotReference())
                || !Objects.equals(pending.date(), draft.preferredDate())
                || !Objects.equals(pending.startsAt(), draft.exactTime())) return false;

        try {
            return Objects.equals(pending.confirmationRef(),
                    bookingExecution.confirmationBinding(execution, draft, availability));
        } catch (RuntimeException invalidBinding) {
            return false;
        }
    }

    static NormalizedUserTurn.Confirmation explicitAnswer(NormalizedUserTurn turn) {
        if (turn == null) return NormalizedUserTurn.Confirmation.NONE;
        String raw = turn.rawText() == null ? "" : turn.rawText().strip().replaceAll("[,،]+", " ")
                .replaceAll("\\s+", " ");
        if (EXPLICIT_NEGATIVE.matcher(raw).matches()) return NormalizedUserTurn.Confirmation.NEGATIVE;
        if (EXPLICIT_POSITIVE.matcher(raw).matches()) return NormalizedUserTurn.Confirmation.POSITIVE;
        if (turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE
                && EXPLICIT_NEGATIVE.matcher(turn.normalizedText() == null ? "" : turn.normalizedText().strip()).matches()) {
            return NormalizedUserTurn.Confirmation.NEGATIVE;
        }
        if (turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE
                && EXPLICIT_POSITIVE.matcher(turn.normalizedText() == null ? "" : turn.normalizedText().strip()).matches()) {
            return NormalizedUserTurn.Confirmation.POSITIVE;
        }
        return NormalizedUserTurn.Confirmation.NONE;
    }
}
