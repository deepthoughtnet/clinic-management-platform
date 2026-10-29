package com.deepthoughtnet.clinic.api.patientportal.booking;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Executes only a fully prepared Voice booking through the Phase 3A shared application boundary. */
@Service
public class VoiceBookingExecutionService {
    private static final Logger log = LoggerFactory.getLogger(VoiceBookingExecutionService.class);
    private final AppointmentBookingApplicationService bookingApplicationService;

    public VoiceBookingExecutionService(AppointmentBookingApplicationService bookingApplicationService) {
        this.bookingApplicationService = bookingApplicationService;
    }

    public String confirmationBinding(BookingExecutionContext context, BookingDraft draft,
                                      AvailabilityResult availability) {
        if (context == null || draft == null || availability == null || draft.selectedProvider() == null
                || draft.patientSubjectId() == null || draft.preferredDate() == null || draft.exactTime() == null
                || draft.selectedSlotReference() == null || draft.selectedSlotReference().isBlank()
                || availability.requestId() == null || availability.criteriaFingerprint() == null
                || !context.tenantId().toString().equals(draft.tenantScope())
                || !context.patientId().equals(draft.patientSubjectId())
                || !draft.draftId().equals(availability.draftId())
                || draft.revision() != availability.draftRevision()
                || !java.util.Objects.equals(draft.selectedProvider().doctorId(), availability.doctorId())
                || !java.util.Objects.equals(draft.preferredDate(), availability.date())) {
            throw new IllegalArgumentException("Prepared canonical booking state is incomplete");
        }
        String material = String.join("|", context.tenantId().toString(), context.patientId().toString(),
                context.providerCallId(), context.sessionId(), draft.draftId().toString(),
                Long.toString(draft.revision()), draft.patientSubjectId().toString(),
                safe(draft.selectedProvider().doctorId()), safe(draft.selectedProvider().clinicId()),
                draft.preferredDate().toString(), draft.exactTime().toString(), draft.selectedSlotReference(),
                availability.requestId().toString(), availability.criteriaFingerprint());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public AppointmentRecord book(BookingExecutionContext context, BookingDraft draft,
                                  AvailabilityResult availability, String idempotencyKey,
                                  String confirmationBinding) {
        String currentBinding = confirmationBinding(context, draft, availability);
        if (confirmationBinding == null || !MessageDigest.isEqual(
                currentBinding.getBytes(StandardCharsets.US_ASCII),
                confirmationBinding.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("Voice booking confirmation no longer matches the prepared draft");
        }
        ProviderCandidate provider = draft.selectedProvider();
        UUID doctorId;
        try {
            doctorId = UUID.fromString(provider.doctorId());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Canonical provider identity is invalid");
        }
        if (availability.slots().stream().noneMatch(slot -> draft.selectedSlotReference().equals(slot.slotReference())
                && draft.exactTime().equals(slot.startsAt()))) {
            throw new IllegalArgumentException("Selected slot is not in the current canonical availability result");
        }
        String fingerprint = currentBinding;
        String bookingReference = provider.bookingReference();
        // CARE_PRIVATE availability and final validation use the tenant-local
        // canonical slot identity. A public/external reference on a provider
        // projection must not switch that identity to the external slot system.
        boolean hasBookingReference = provider.source() != ProviderSource.CARE_PRIVATE
                && bookingReference != null && !bookingReference.isBlank();
        if (!hasBookingReference && provider.source() != ProviderSource.CARE_PRIVATE) {
            throw new IllegalArgumentException("Canonical booking target is missing");
        }
        BookingTargetReference target = hasBookingReference
                ? new BookingTargetReference(bookingReference, 0L) : null;
        log.info("VOICE_BOOKING_EXECUTION stage=BOOKING_APPLICATION_SERVICE_INVOKED tenantId={} providerCallId={} "
                        + "interactionId={} draftId={} draftRevision={} providerSource={} date={} "
                        + "selectedSlotReferencePresent={} bookingTargetReferencePresent={} idempotencyKeyPresent={}",
                context.tenantId(), context.providerCallId(), context.interactionId(), draft.draftId(),
                draft.revision(), provider.source(), draft.preferredDate(), true, hasBookingReference,
                idempotencyKey != null && !idempotencyKey.isBlank());
        AppointmentRecord booked = bookingApplicationService.book(context,
                new AppointmentBookingApplicationService.BookingCommand(context.tenantId(), context.patientId(),
                        doctorId, draft.preferredDate(), draft.exactTime(), draft.selectedSlotReference(), target,
                        idempotencyKey, fingerprint, "AIVA V2 Voice booking"));
        log.info("VOICE_BOOKING_EXECUTION stage=BOOKING_APPLICATION_SERVICE_RESULT tenantId={} providerCallId={} "
                        + "interactionId={} draftId={} result=SUCCESS",
                context.tenantId(), context.providerCallId(), context.interactionId(), draft.draftId());
        return booked;
    }

    /**
     * Read-only lookup for a prior commit. It never invokes availability validation or mutation;
     * the shared application boundary scopes the receipt to the authorized tenant and patient.
     */
    public Optional<AppointmentRecord> reconcile(BookingExecutionContext context, BookingDraft draft,
            AvailabilityResult availability, String idempotencyKey, String confirmationBinding) {
        String currentBinding = confirmationBinding(context, draft, availability);
        if (confirmationBinding == null || !MessageDigest.isEqual(
                currentBinding.getBytes(StandardCharsets.US_ASCII),
                confirmationBinding.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("Voice booking confirmation no longer matches the prepared draft");
        }
        return bookingApplicationService.findExistingBooking(context,
                bookingCommand(context, draft, idempotencyKey, currentBinding));
    }

    private AppointmentBookingApplicationService.BookingCommand bookingCommand(
            BookingExecutionContext context, BookingDraft draft, String idempotencyKey, String fingerprint) {
        ProviderCandidate provider = draft.selectedProvider();
        UUID doctorId;
        try { doctorId = UUID.fromString(provider.doctorId()); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Canonical provider identity is invalid"); }
        String bookingReference = provider.bookingReference();
        boolean hasBookingReference = provider.source() != ProviderSource.CARE_PRIVATE
                && bookingReference != null && !bookingReference.isBlank();
        if (!hasBookingReference && provider.source() != ProviderSource.CARE_PRIVATE) {
            throw new IllegalArgumentException("Canonical booking target is missing");
        }
        BookingTargetReference target = hasBookingReference
                ? new BookingTargetReference(bookingReference, 0L) : null;
        return new AppointmentBookingApplicationService.BookingCommand(context.tenantId(), context.patientId(),
                doctorId, draft.preferredDate(), draft.exactTime(), draft.selectedSlotReference(), target,
                idempotencyKey, fingerprint, "AIVA V2 Voice booking");
    }

    private String safe(String value) { return value == null ? "" : value; }
}
