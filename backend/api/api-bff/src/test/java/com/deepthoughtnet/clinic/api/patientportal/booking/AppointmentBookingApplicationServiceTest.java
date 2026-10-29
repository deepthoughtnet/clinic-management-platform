package com.deepthoughtnet.clinic.api.patientportal.booking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.reliability.service.IdempotencyService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentBookingApplicationServiceTest {
    private final AppointmentService appointments = mock(AppointmentService.class);
    private final PatientService patients = mock(PatientService.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final AppointmentBookingApplicationService service = new AppointmentBookingApplicationService(
            appointments, patients, null, idempotency, new ObjectMapper().findAndRegisterModules());

    @Test
    void rejectsCrossPatientBeforeMutation() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID otherPatient = UUID.randomUUID();
        BookingExecutionContext context = new BookingExecutionContext(tenant, patient,
                BookingExecutionContext.ActorType.PATIENT_PORTAL_USER, UUID.randomUUID(), "WEB",
                BookingExecutionContext.AuthenticationState.AUTHENTICATED, "PORTAL", "session", null,
                null, "corr", ZoneId.of("UTC"), BookingExecutionContext.Operation.BOOK_APPOINTMENT, null, true);
        var command = new AppointmentBookingApplicationService.BookingCommand(tenant, otherPatient,
                UUID.randomUUID(), LocalDate.now().plusDays(1), LocalTime.of(10, 0), "slot", null,
                "key", "fingerprint", null);

        assertThatThrownBy(() -> service.book(context, command)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(appointments, patients, idempotency);
    }

    @Test
    void rejectsGuestContextWithoutVoiceCallProvenance() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        BookingExecutionContext context = new BookingExecutionContext(tenant, patient,
                BookingExecutionContext.ActorType.VOICE_GUEST_PATIENT, null, "VOICE",
                BookingExecutionContext.AuthenticationState.GUEST_REGISTERED, "VOICE_GUEST_REGISTRATION",
                "session", null, null, null, ZoneId.of("UTC"), BookingExecutionContext.Operation.BOOK_APPOINTMENT,
                "VOICE_GUEST_BOOKING", true);
        var command = new AppointmentBookingApplicationService.BookingCommand(tenant, patient,
                UUID.randomUUID(), LocalDate.now().plusDays(1), LocalTime.of(10, 0), "slot", null,
                null, "fingerprint", null);

        assertThatThrownBy(() -> service.book(context, command)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(appointments, patients, idempotency);
    }

    @Test
    void currentCanonicalSlotReachesDomainWithExplicitAuditActor() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        LocalDate date = LocalDate.now(ZoneId.of("UTC")).plusDays(3);
        LocalTime time = LocalTime.of(10, 30);
        String reference = "care-slot-" + UUID.nameUUIDFromBytes(String.join("|", "care-slot", tenant.toString(),
                doctor.toString(), date.toString(), time.toString()).getBytes(StandardCharsets.UTF_8));
        when(patients.findById(tenant, patient)).thenReturn(Optional.of(new PatientRecord(patient, tenant, "P-1",
                "Test", "Patient", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, true, null, null)));
        when(appointments.listSlots(eq(tenant), eq(doctor), eq(date), any())).thenReturn(List.of(
                new DoctorAvailabilitySlotRecord(doctor, "Dr Example", date, time, time.plusMinutes(30), null,
                        0, 1, true, null, false, false, true, null, null, null, null, null, null, null, null)));
        AppointmentRecord booked = new AppointmentRecord(UUID.randomUUID(), tenant, patient, "P-1", "Test Patient",
                null, doctor, "Dr Example", null, date, time, null, reference, null,
                AppointmentType.SCHEDULED, AppointmentPriority.NORMAL, AppointmentStatus.BOOKED,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(appointments.createScheduled(eq(tenant), any(), isNull(), eq(false), any(), any())).thenReturn(booked);
        BookingExecutionContext context = new BookingExecutionContext(tenant, patient,
                BookingExecutionContext.ActorType.VOICE_GUEST_PATIENT, null, "VOICE",
                BookingExecutionContext.AuthenticationState.GUEST_REGISTERED, "VOICE_GUEST_REGISTRATION",
                "session", "call", UUID.randomUUID(), "corr", ZoneId.of("UTC"),
                BookingExecutionContext.Operation.BOOK_APPOINTMENT, "VOICE_GUEST_BOOKING", true);
        var command = new AppointmentBookingApplicationService.BookingCommand(tenant, patient, doctor, date, time,
                reference, null, "idempotency", "request-fingerprint", "consultation");

        assertThat(service.book(context, command)).isSameAs(booked);
        verify(appointments).createScheduled(eq(tenant), any(), isNull(), eq(false), eq(ZoneId.of("UTC")),
                argThat(actor -> actor.equals(new AuditActorContext("VOICE_GUEST_PATIENT", null, "VOICE",
                        context.interactionId(), "corr"))));
    }

    @Test
    void staleLocalCanonicalSlotDoesNotReachAppointmentMutation() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        LocalDate date = LocalDate.now(ZoneId.of("UTC")).plusDays(3);
        LocalTime time = LocalTime.of(10, 30);
        String reference = localSlotReference(tenant, doctor, date, time);
        when(patients.findById(tenant, patient)).thenReturn(Optional.of(activePatient(patient, tenant)));
        when(idempotency.findCachedResponse(any(), anyString(), anyString())).thenReturn(Optional.empty());
        when(appointments.listSlots(eq(tenant), eq(doctor), eq(date), any())).thenReturn(List.of());

        var command = new AppointmentBookingApplicationService.BookingCommand(tenant, patient, doctor, date, time,
                reference, null, "stale-key", "stale-fingerprint", "consultation");
        assertThatThrownBy(() -> service.book(voiceGuestContext(tenant, patient), command))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(appointments, never()).createScheduled(any(), any(), isNull(), anyBoolean(), any(), any());
    }

    @Test
    void idempotencyReplayReturnsExistingBookingWithoutSecondDomainMutation() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        LocalDate date = LocalDate.now(ZoneId.of("UTC")).plusDays(3);
        LocalTime time = LocalTime.of(10, 30);
        BookingExecutionContext context = voiceGuestContext(tenant, patient);
        UUID idempotencyScope = UUID.nameUUIDFromBytes((tenant + ":" + patient).getBytes(StandardCharsets.UTF_8));
        var command = new AppointmentBookingApplicationService.BookingCommand(tenant, patient, doctor, date, time,
                "canonical-slot", null, "stable-key", "same-confirmed-request", "consultation");
        AppointmentRecord previous = new AppointmentRecord(UUID.randomUUID(), tenant, patient, "P-1", "Test Patient",
                null, doctor, "Dr Example", null, date, time, null, "APT-REPLAY", null,
                AppointmentType.SCHEDULED, AppointmentPriority.NORMAL, AppointmentStatus.BOOKED,
                OffsetDateTime.now(), OffsetDateTime.now());
        when(patients.findById(tenant, patient)).thenReturn(Optional.of(activePatient(patient, tenant)));
        when(idempotency.findCachedResponse(idempotencyScope, "booking-mutation:stable-key", "same-confirmed-request"))
                .thenReturn(Optional.of(new ObjectMapper().findAndRegisterModules().writeValueAsString(previous)));

        AppointmentRecord replay = service.book(context, command);

        assertThat(replay.id()).isEqualTo(previous.id());
        verifyNoInteractions(appointments);
    }

    @Test
    void durableReceiptLookupScopesSameCallAndExecutionKeyByTenantAndPatient() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        UUID doctorA = UUID.randomUUID();
        UUID doctorB = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 12);
        LocalTime time = LocalTime.of(10, 30);
        var commandA = new AppointmentBookingApplicationService.BookingCommand(tenantA, patientA, doctorA,
                date, time, "same-slot-ref", null, "same-execution-key", "same-fingerprint", "consultation");
        var commandB = new AppointmentBookingApplicationService.BookingCommand(tenantB, patientB, doctorB,
                date, time, "same-slot-ref", null, "same-execution-key", "same-fingerprint", "consultation");
        when(patients.findById(tenantA, patientA)).thenReturn(Optional.of(activePatient(patientA, tenantA)));
        when(patients.findById(tenantB, patientB)).thenReturn(Optional.of(activePatient(patientB, tenantB)));
        when(idempotency.findCachedResponse(any(), anyString(), anyString())).thenReturn(Optional.empty());

        assertThat(service.findExistingBooking(voiceGuestContext(tenantA, patientA), commandA)).isEmpty();
        assertThat(service.findExistingBooking(voiceGuestContext(tenantB, patientB), commandB)).isEmpty();

        var scopes = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(idempotency, times(2)).findCachedResponse(scopes.capture(),
                eq("booking-mutation:same-execution-key"), eq("same-fingerprint"));
        assertThat(scopes.getAllValues()).hasSize(2).doesNotHaveDuplicates();
        verifyNoInteractions(appointments);
    }

    private BookingExecutionContext voiceGuestContext(UUID tenant, UUID patient) {
        return new BookingExecutionContext(tenant, patient, BookingExecutionContext.ActorType.VOICE_GUEST_PATIENT,
                null, "VOICE", BookingExecutionContext.AuthenticationState.GUEST_REGISTERED,
                "VOICE_GUEST_REGISTRATION", "session", "call", UUID.randomUUID(), "corr", ZoneId.of("UTC"),
                BookingExecutionContext.Operation.BOOK_APPOINTMENT, "VOICE_GUEST_BOOKING", true);
    }

    private PatientRecord activePatient(UUID patient, UUID tenant) {
        return new PatientRecord(patient, tenant, "P-1", "Test", "Patient", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, true, null, null);
    }

    private String localSlotReference(UUID tenant, UUID doctor, LocalDate date, LocalTime time) {
        String source = String.join("|", "care-slot", tenant.toString(), doctor.toString(), date.toString(), time.toString());
        return "care-slot-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}
