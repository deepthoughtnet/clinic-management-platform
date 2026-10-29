package com.deepthoughtnet.clinic.api.patientportal.booking;

import com.deepthoughtnet.clinic.api.appointment.AppointmentTimingRules;
import com.deepthoughtnet.clinic.api.reliability.service.IdempotencyService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditActorContext;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingSlotSummary;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.port.HealthcareAvailabilityPort;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Shared application boundary. Web authorization remains in its adapter; this service validates explicit scope. */
@Service
public class AppointmentBookingApplicationService {
    private final AppointmentService appointmentService;
    private final PatientService patientService;
    private final HealthcareAvailabilityPort availabilityPort;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public AppointmentBookingApplicationService(AppointmentService appointmentService, PatientService patientService,
                                                HealthcareAvailabilityPort availabilityPort,
                                                IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.appointmentService = appointmentService;
        this.patientService = patientService;
        this.availabilityPort = availabilityPort;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AppointmentRecord book(BookingExecutionContext context, BookingCommand command) {
        validateContext(context, command);
        PatientRecord patient = validateActivePatient(context);
        Optional<AppointmentRecord> cached = findCachedBooking(context, command);
        if (cached.isPresent()) return cached.get();
        String scopedIdempotencyKey = scopedIdempotencyKey(command.idempotencyKey());
        UUID idempotencyScope = idempotencyScope(context);
        validateCanonicalSlot(context, command);
        AuditActorContext auditActor = new AuditActorContext(context.actorType().name(),
                context.actorId() == null ? null : context.actorId().toString(), context.channel(),
                context.interactionId(), context.correlationId());
        AppointmentRecord booked = appointmentService.createScheduled(context.tenantId(), new AppointmentUpsertCommand(
                        patient.id(), command.doctorUserId(), command.date(), command.time(), command.reason(),
                        AppointmentType.SCHEDULED, null, AppointmentPriority.NORMAL, false),
                context.actorId(), false, context.timezone(), auditActor);
        if (scopedIdempotencyKey != null) {
            try { idempotencyService.storeResponse(idempotencyScope, scopedIdempotencyKey,
                    command.requestFingerprint(), objectMapper.writeValueAsString(booked)); }
            catch (Exception ex) { throw new IllegalStateException("Unable to cache booking result", ex); }
        }
        return booked;
    }

    /** Read-only reconciliation against the existing durable idempotency receipt. */
    @Transactional(readOnly = true)
    public Optional<AppointmentRecord> findExistingBooking(BookingExecutionContext context, BookingCommand command) {
        validateContext(context, command);
        validateActivePatient(context);
        return findCachedBooking(context, command);
    }

    private Optional<AppointmentRecord> findCachedBooking(BookingExecutionContext context, BookingCommand command) {
        String scopedKey = scopedIdempotencyKey(command.idempotencyKey());
        if (scopedKey == null) return Optional.empty();
        UUID idempotencyScope = idempotencyScope(context);
        return idempotencyService.findCachedResponse(idempotencyScope, scopedKey, command.requestFingerprint())
                .map(this::readCachedBooking);
    }

    private AppointmentRecord readCachedBooking(String responseJson) {
        try { return objectMapper.readValue(responseJson, AppointmentRecord.class); }
        catch (Exception ex) { throw new IllegalStateException("Unable to read cached booking result", ex); }
    }

    private PatientRecord validateActivePatient(BookingExecutionContext context) {
        return patientService.findById(context.tenantId(), context.patientId())
                .filter(record -> record.active() && record.tenantId().equals(context.tenantId()))
                .orElseThrow(() -> new SecurityException("Booking patient is not active in the execution tenant"));
    }

    private UUID idempotencyScope(BookingExecutionContext context) {
        return UUID.nameUUIDFromBytes((context.tenantId() + ":" + context.patientId())
                .getBytes(StandardCharsets.UTF_8));
    }

    private String scopedIdempotencyKey(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank() ? null : "booking-mutation:" + idempotencyKey;
    }

    private void validateContext(BookingExecutionContext context, BookingCommand command) {
        if (context == null || command == null || !context.authorizationValidated()
                || context.allowedOperation() != BookingExecutionContext.Operation.BOOK_APPOINTMENT
                || !context.tenantId().equals(command.tenantId())
                || !context.patientId().equals(command.patientId())) {
            throw new SecurityException("Booking execution context does not authorize this patient and tenant");
        }
        if (context.actorType() == BookingExecutionContext.ActorType.VOICE_GUEST_PATIENT
                && (context.authenticationState() != BookingExecutionContext.AuthenticationState.GUEST_REGISTERED
                || context.actorId() != null || !"VOICE_GUEST_BOOKING".equals(context.guestRegistrationSource())
                || !"VOICE_GUEST_REGISTRATION".equals(context.authorizationSource())
                || !"VOICE".equals(context.channel()) || context.providerCallId() == null
                || context.sessionId() == null || context.interactionId() == null)) {
            throw new SecurityException("Guest booking actor scope is invalid");
        }
        if (context.actorType() == BookingExecutionContext.ActorType.VOICE_REGISTERED_UNVERIFIED
                && (context.authenticationState() != BookingExecutionContext.AuthenticationState.REGISTERED_UNVERIFIED
                || context.actorId() != null || !"VOICE_RESTRICTED_NEW_BOOKING".equals(context.authorizationSource())
                || !"VOICE_RESTRICTED_NEW_BOOKING".equals(context.guestRegistrationSource())
                || !"VOICE".equals(context.channel()) || context.providerCallId() == null
                || context.sessionId() == null || context.interactionId() == null)) {
            throw new SecurityException("Restricted unverified Voice booking actor scope is invalid");
        }
        if (context.authenticationState() == BookingExecutionContext.AuthenticationState.AUTHENTICATED
                && context.actorId() == null) {
            throw new SecurityException("Authenticated booking actor is missing its truthful user identity");
        }
        if (context.actorType() == BookingExecutionContext.ActorType.VOICE_AUTHENTICATED_PATIENT
                && (!"VOICE".equals(context.channel())
                || !("VOICE_OTP".equals(context.authorizationSource())
                    && context.authenticationState() == BookingExecutionContext.AuthenticationState.AUTHENTICATED
                    || "PREVERIFIED_PHONE".equals(context.authorizationSource())
                    && context.authenticationState() == BookingExecutionContext.AuthenticationState.REGISTERED_PHONE_VERIFIED)
                || context.providerCallId() == null || context.sessionId() == null)) {
            throw new SecurityException("Authenticated Voice booking actor scope is invalid");
        }
        if (context.authenticationState() == BookingExecutionContext.AuthenticationState.REGISTERED_PHONE_VERIFIED
                && (context.actorType() != BookingExecutionContext.ActorType.VOICE_AUTHENTICATED_PATIENT
                || !"VOICE".equals(context.channel()) || !"PREVERIFIED_PHONE".equals(context.authorizationSource())
                || context.providerCallId() == null || context.sessionId() == null)) {
            throw new SecurityException("Preverified Voice booking actor scope is invalid");
        }
        if (context.actorType() == BookingExecutionContext.ActorType.PATIENT_PORTAL_USER
                && (!"WEB".equals(context.channel()) || !"AUTHENTICATED_PATIENT_PORTAL_PRINCIPAL".equals(context.authorizationSource()))) {
            throw new SecurityException("Patient Portal booking actor scope is invalid");
        }
    }

    private void validateCanonicalSlot(BookingExecutionContext context, BookingCommand command) {
        if (command.date() == null || command.time() == null || command.doctorUserId() == null
                || command.slotReference() == null || command.slotReference().isBlank()) {
            throw new IllegalArgumentException("Canonical selected slot is required");
        }
        if (command.bookingTargetReference() != null) {
            if (availabilityPort == null) throw staleSlot();
            BookingSlotSummary matched = availabilityPort.getBookableSlots(command.bookingTargetReference(), command.date())
                    .stream().filter(slot -> command.slotReference().equals(slot.slotId())).findFirst().orElseThrow(this::staleSlot);
            if (matched.startsAt() == null || !command.date().equals(matched.startsAt().toLocalDate())
                    || !command.time().equals(matched.startsAt().toLocalTime())) throw staleSlot();
        } else {
            String expected = localSlotReference(context.tenantId(), command.doctorUserId(), command.date(), command.time());
            if (!expected.equals(command.slotReference())) throw staleSlot();
        }
        List<DoctorAvailabilitySlotRecord> current = appointmentService.listSlots(
                context.tenantId(), command.doctorUserId(), command.date(), context.timezone());
        ZonedDateTime now = ZonedDateTime.now(context.timezone());
        boolean selectable = current.stream()
                .filter(slot -> AppointmentTimingRules.isSlotBookableForPatient(slot.appointmentDate(), slot.slotTime(), context.timezone(), now))
                .anyMatch(slot -> command.time().equals(slot.slotTime()) && slot.selectable());
        if (!selectable) throw staleSlot();
    }

    private ResponseStatusException staleSlot() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "This time is no longer available. Please choose another slot.");
    }

    private String localSlotReference(UUID tenantId, UUID doctorUserId, java.time.LocalDate date, java.time.LocalTime time) {
        String source = String.join("|", "care-slot", tenantId.toString(), doctorUserId.toString(), date.toString(), time.toString());
        return "care-slot-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    public record BookingCommand(UUID tenantId, UUID patientId, UUID doctorUserId, java.time.LocalDate date,
                                 java.time.LocalTime time, String slotReference,
                                 BookingTargetReference bookingTargetReference, String idempotencyKey,
                                 String requestFingerprint, String reason) { }
}
