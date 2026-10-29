package com.deepthoughtnet.clinic.api.patientportal.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.api.reliability.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class VoiceBookingExecutionServiceTest {
    private final AppointmentBookingApplicationService sharedBoundary = mock(AppointmentBookingApplicationService.class);
    private final VoiceBookingExecutionService service = new VoiceBookingExecutionService(sharedBoundary);

    @Test
    void confirmationBindsDraftPatientProviderTimeCanonicalSlotAvailabilityAndCall() {
        Fixture fixture = fixture();
        String initial = service.confirmationBinding(fixture.context(), fixture.draft(), fixture.availability());
        assertThat(initial).isNotBlank();
        assertThat(service.confirmationBinding(fixture.context(), fixture.draft(), new AvailabilityResult(
                fixture.availability().requestId(), fixture.availability().draftId(),
                fixture.availability().draftRevision(), "changed-fingerprint", fixture.availability().providerHandle(),
                fixture.availability().doctorId(), fixture.availability().clinicId(), fixture.availability().date(),
                null, null, "UTC", fixture.availability().slots(), null, false, Instant.now(),
                Instant.now().plusSeconds(60), 0, 3))).isNotEqualTo(initial);
        BookingExecutionContext otherCall = context(fixture.tenant(), fixture.patient(), "other-call");
        assertThat(service.confirmationBinding(otherCall, fixture.draft(), fixture.availability())).isNotEqualTo(initial);
    }

    @Test
    void executionPassesOnlyTheCanonicalSelectedSlotToSharedBoundary() {
        Fixture fixture = fixture();
        String binding = service.confirmationBinding(fixture.context(), fixture.draft(), fixture.availability());
        when(sharedBoundary.book(any(), any())).thenReturn(new AppointmentRecord(UUID.randomUUID(), fixture.tenant(),
                fixture.patient(), "P-1", "Guest", null, UUID.randomUUID(), "Dr Example", null, fixture.date(),
                LocalTime.of(16, 0), null, "APT-1", null,
                com.deepthoughtnet.clinic.appointment.service.model.AppointmentType.SCHEDULED,
                com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority.NORMAL,
                com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus.BOOKED,
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now()));

        service.book(fixture.context(), fixture.draft(), fixture.availability(), "stable-key", binding);

        verify(sharedBoundary).book(any(), org.mockito.ArgumentMatchers.argThat(command ->
                command.slotReference().equals("canonical-slot")
                        && command.date().equals(fixture.date())
                        && command.time().equals(LocalTime.of(16, 0))
                        && command.idempotencyKey().equals("stable-key")
                        && command.bookingTargetReference() != null));
    }

    @Test
    void carePrivateProviderWithoutExternalBookingTargetUsesCanonicalLocalSlotReference() {
        Fixture original = fixture();
        ProviderCandidate provider = new ProviderCandidate(original.draft().selectedProvider().providerHandle(),
                original.draft().selectedProvider().providerHandle(), original.draft().selectedProvider().doctorId(),
                original.draft().selectedProvider().clinicId(), original.draft().selectedProvider().tenantId(),
                original.draft().selectedProvider().clinicSlug(), "stale-public-projection-reference",
                original.draft().selectedProvider().displayName(), original.draft().selectedProvider().specialty(),
                original.draft().selectedProvider().clinicDisplayName(), ProviderSource.CARE_PRIVATE,
                original.draft().selectedProvider().capabilities());
        BookingDraft draft = new BookingDraft(original.draft().draftId(), original.draft().patientSubjectId(),
                original.draft().tenantScope(), provider, original.draft().specialtyFilter(), original.date(), null,
                original.draft().exactTime(), null, original.draft().latestAvailabilityRequestId(),
                original.draft().selectedSlotReference(), AivaV2Models.DraftStatus.PREPARED,
                original.draft().revision(), original.draft().expiresAt(), null);
        String binding = service.confirmationBinding(original.context(), draft, original.availability());
        when(sharedBoundary.book(any(), any())).thenReturn(new AppointmentRecord(UUID.randomUUID(), original.tenant(),
                original.patient(), "P-1", "Guest", null, UUID.randomUUID(), "Dr Example", null, original.date(),
                LocalTime.of(16, 0), null, "APT-1", null, AppointmentType.SCHEDULED, AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED, java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now()));

        service.book(original.context(), draft, original.availability(), "stable-key", binding);

        verify(sharedBoundary).book(any(), org.mockito.ArgumentMatchers.argThat(command ->
                command.slotReference().equals("canonical-slot") && command.bookingTargetReference() == null));
    }

    @Test
    void carePrivateRevalidationUsesSameTenantLocalSlotDespiteStaleProjectionReference() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        LocalDate date = LocalDate.now(ZoneId.of("UTC")).plusDays(3);
        LocalTime time = LocalTime.of(18, 0);
        String localReference = "care-slot-" + UUID.nameUUIDFromBytes(String.join("|", "care-slot",
                tenant.toString(), doctor.toString(), date.toString(), time.toString())
                .getBytes(StandardCharsets.UTF_8));
        ProviderCandidate provider = new ProviderCandidate("handle", "provider", doctor.toString(), "clinic",
                tenant.toString(), "clinic", "stale-public-reference", "Dr Example", "General Medicine",
                "Clinic", ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(true, true, false, false, true, true, true));
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), patient, tenant.toString(), provider, null,
                date, null, time, null, UUID.randomUUID(), localReference, AivaV2Models.DraftStatus.PREPARED,
                4, Instant.now().plusSeconds(600), null);
        AvailabilityResult availability = new AvailabilityResult(draft.latestAvailabilityRequestId(), draft.draftId(),
                draft.revision(), "fingerprint", provider.providerHandle(), doctor.toString(), provider.clinicId(),
                date, null, null, "UTC", List.of(new AvailabilitySlot(localReference, time, time.plusMinutes(30),
                "6 PM")), null, false, Instant.now(), Instant.now().plusSeconds(60), 0, 3);
        AppointmentService appointments = mock(AppointmentService.class);
        PatientService patients = mock(PatientService.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        when(patients.findById(tenant, patient)).thenReturn(Optional.of(new PatientRecord(patient, tenant,
                "P-1", "Guest", "Patient", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, true, null, null)));
        when(idempotency.findCachedResponse(any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(appointments.listSlots(org.mockito.ArgumentMatchers.eq(tenant), org.mockito.ArgumentMatchers.eq(doctor),
                org.mockito.ArgumentMatchers.eq(date), any())).thenReturn(List.of(
                new DoctorAvailabilitySlotRecord(doctor, "Dr Example", date, time, time.plusMinutes(30), null,
                        0, 1, true, null, false, false, true, null, null, null, null, null, null, null, null)));
        AppointmentRecord booked = new AppointmentRecord(UUID.randomUUID(), tenant, patient, "P-1", "Guest Patient",
                null, doctor, "Dr Example", null, date, time, null, "APT-LOCAL", null, AppointmentType.SCHEDULED,
                AppointmentPriority.NORMAL, AppointmentStatus.BOOKED, OffsetDateTime.now(), OffsetDateTime.now());
        when(appointments.createScheduled(org.mockito.ArgumentMatchers.eq(tenant), any(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(false), any(), any()))
                .thenReturn(booked);
        AppointmentBookingApplicationService shared = new AppointmentBookingApplicationService(appointments, patients,
                null, idempotency, new ObjectMapper().findAndRegisterModules());
        VoiceBookingExecutionService execution = new VoiceBookingExecutionService(shared);
        BookingExecutionContext context = context(tenant, patient, "call-care-private");
        String binding = execution.confirmationBinding(context, draft, availability);

        assertThat(execution.book(context, draft, availability, "stable-key", binding)).isSameAs(booked);

        verify(appointments).listSlots(org.mockito.ArgumentMatchers.eq(tenant), org.mockito.ArgumentMatchers.eq(doctor),
                org.mockito.ArgumentMatchers.eq(date), any());
        verify(appointments).createScheduled(org.mockito.ArgumentMatchers.eq(tenant), any(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(false), any(), any());
    }

    @Test
    void mismatchedFinalConfirmationNeverCallsSharedMutationBoundary() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> service.book(fixture.context(), fixture.draft(), fixture.availability(), "stable",
                "wrong-binding")).isInstanceOf(SecurityException.class);
        org.mockito.Mockito.verifyNoInteractions(sharedBoundary);
    }

    private Fixture fixture() {
        UUID tenant = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 5);
        ProviderCandidate provider = new ProviderCandidate("handle", "provider", doctor.toString(), "clinic",
                tenant.toString(), "clinic-slug", "opaque-booking-reference", "Dr Example", "General Medicine",
                "Clinic", ProviderSource.DISCOVER_PUBLIC,
                new ProviderCapabilities(true, true, false, false, true, true, true));
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), patient, tenant.toString(), provider,
                null, date, null, LocalTime.of(16, 0), null, UUID.randomUUID(), "canonical-slot",
                AivaV2Models.DraftStatus.PREPARED, 4, Instant.now().plusSeconds(900), null);
        AvailabilityResult availability = new AvailabilityResult(draft.latestAvailabilityRequestId(), draft.draftId(),
                draft.revision(), "fingerprint", provider.providerHandle(), provider.doctorId(), provider.clinicId(),
                date, null, null, "UTC", List.of(new AvailabilitySlot("canonical-slot", LocalTime.of(16, 0),
                LocalTime.of(16, 30), "4 PM")), null, false, Instant.now(), Instant.now().plusSeconds(60), 0, 3);
        return new Fixture(tenant, patient, date, context(tenant, patient, "call-1"), draft, availability);
    }

    private BookingExecutionContext context(UUID tenant, UUID patient, String call) {
        return new BookingExecutionContext(tenant, patient, BookingExecutionContext.ActorType.VOICE_GUEST_PATIENT,
                null, "VOICE", BookingExecutionContext.AuthenticationState.GUEST_REGISTERED,
                "VOICE_GUEST_REGISTRATION", call, call, UUID.randomUUID(), "corr", ZoneId.of("UTC"),
                BookingExecutionContext.Operation.BOOK_APPOINTMENT, "VOICE_GUEST_BOOKING", true);
    }

    private record Fixture(UUID tenant, UUID patient, LocalDate date, BookingExecutionContext context,
                           BookingDraft draft, AvailabilityResult availability) { }
}
