package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceDataService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotStatus;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotTimeState;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorUnavailabilityRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorUnavailabilityType;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceToolExecutor.QueryType;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn.Daypart;
import java.time.DayOfWeek;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HealthcareAiReadOnlyToolGatewayTest {
    @Test
    void voiceDoctorLookupUsesSuppliedTenantWithoutPortalPatientContext() {
        PatientPortalService portal = mock(PatientPortalService.class);
        UUID tenantId = UUID.randomUUID();
        when(portal.doctorsForTenant(tenantId)).thenReturn(List.of(
                new PatientPortalDoctorResponse("doctor-1", "Dr Sharma", "Cardiology", null, null, null)));
        HealthcareAiReadOnlyToolGateway gateway = new HealthcareAiReadOnlyToolGateway(
                portal, mock(PublicCatalogFacade.class), mock(DiscoverReferenceDataService.class),
                mock(ClinicProfileService.class), mock(AppointmentService.class));

        var result = gateway.execute("FIND_DOCTOR",
                new HealthcareAiExecutionContext(tenantId, null, "GUEST_CALLER", HealthcareAiChannel.VOICE,
                        UUID.randomUUID(), null, null, "hi-IN"),
                new HealthcareAiReadOnlyToolGateway.ToolInput(null, "Cardiology", null, null, null, null));

        assertTrue(result.allowed());
        assertTrue(result.success());
        assertEquals(1, result.candidateCount());
        verify(portal, never()).currentPatientId();
    }

    @Test
    void voiceDoctorLookupReturnsTenantScopedPediatricsDoctor() {
        PatientPortalService portal = mock(PatientPortalService.class);
        UUID tenantId = UUID.randomUUID();
        when(portal.doctorsForTenant(tenantId)).thenReturn(List.of(
                new PatientPortalDoctorResponse("ravi", "Ravi Mehta", "Pediatrics", null, null, null),
                new PatientPortalDoctorResponse("amit", "Amit Verma", "General Medicine", null, null, null)));
        HealthcareAiReadOnlyToolGateway gateway = new HealthcareAiReadOnlyToolGateway(
                portal, mock(PublicCatalogFacade.class), mock(DiscoverReferenceDataService.class),
                mock(ClinicProfileService.class), mock(AppointmentService.class));

        var result = gateway.execute("FIND_DOCTOR",
                new HealthcareAiExecutionContext(tenantId, null, "GUEST_CALLER", HealthcareAiChannel.VOICE,
                        UUID.randomUUID(), null, null, "hi-IN"),
                new HealthcareAiReadOnlyToolGateway.ToolInput(null, "Pediatrics", null, null, null, null));

        assertTrue(result.success());
        assertEquals(1, result.candidateCount());
        List<?> values = (List<?>) result.value();
        var doctor = (com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse) values.getFirst();
        assertEquals("Ravi Mehta", doctor.doctorDisplayName());
        verify(portal).doctorsForTenant(tenantId);
    }

    @Test
    void dateBasedGenericDoctorDiscoveryReturnsOnlyTenantDoctorsWithBookableSlots() {
        PatientPortalService portal = mock(PatientPortalService.class);
        AppointmentService appointments = mock(AppointmentService.class);
        UUID tenant = UUID.randomUUID();
        UUID availableDoctor = UUID.randomUUID();
        UUID unavailableDoctor = UUID.randomUUID();
        LocalDate requestedDate = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        when(portal.careAiDoctorsForTenant(tenant)).thenReturn(List.of(
                new PatientPortalCareAiDoctorOption(availableDoctor.toString(), "Dr Available", "Pediatrics",
                        UUID.randomUUID(), UUID.randomUUID(), tenant, "clinic", "Clinic"),
                new PatientPortalCareAiDoctorOption(unavailableDoctor.toString(), "Dr Unavailable", "Pediatrics",
                        UUID.randomUUID(), UUID.randomUUID(), tenant, "clinic", "Clinic")));
        when(portal.doctorsForTenant(tenant)).thenReturn(List.of(
                new PatientPortalDoctorResponse(availableDoctor.toString(), "Dr Available", "Pediatrics", null, null, null),
                new PatientPortalDoctorResponse(unavailableDoctor.toString(), "Dr Unavailable", "Pediatrics", null, null, null)));
        when(appointments.listSlots(tenant, availableDoctor, requestedDate, ZoneId.of("Asia/Kolkata")))
                .thenReturn(List.of(new DoctorAvailabilitySlotRecord(availableDoctor, "Dr Available", requestedDate,
                        LocalTime.of(10, 0), LocalTime.of(10, 30), DoctorAvailabilitySlotStatus.AVAILABLE,
                        0, 1, true, DoctorAvailabilitySlotTimeState.FUTURE, false, false, true,
                        null, null, null, null, null, null, null, null)));
        when(appointments.listSlots(tenant, unavailableDoctor, requestedDate, ZoneId.of("Asia/Kolkata")))
                .thenReturn(List.of());
        HealthcareAiReadOnlyToolGateway gateway = new HealthcareAiReadOnlyToolGateway(
                portal, mock(PublicCatalogFacade.class), mock(DiscoverReferenceDataService.class),
                mock(ClinicProfileService.class), appointments, Clock.fixed(
                        requestedDate.minusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant(),
                        ZoneId.of("UTC")));
        HealthcareAiExecutionContext context = new HealthcareAiExecutionContext(tenant, null, "GUEST_CALLER",
                HealthcareAiChannel.VOICE, UUID.randomUUID(), null, null, "hi-IN");

        var result = gateway.execute("FIND_DOCTOR", context,
                new HealthcareAiReadOnlyToolGateway.ToolInput(null, "Pediatrics", null, null,
                        null, requestedDate, QueryType.EFFECTIVE_DATE_AVAILABILITY, null,
                        ZoneId.of("Asia/Kolkata"), 0));

        assertEquals(1, result.candidateCount());
        assertEquals("Dr Available", ((com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse)
                ((List<?>) result.value()).getFirst()).doctorDisplayName());
        verify(appointments).listSlots(tenant, availableDoctor, requestedDate, ZoneId.of("Asia/Kolkata"));
        verify(appointments).listSlots(tenant, unavailableDoctor, requestedDate, ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void voiceDoctorLookupReturnsGeneralMedicineDoctorAndSourceDiagnostics() {
        PatientPortalService portal = mock(PatientPortalService.class);
        UUID tenantId = UUID.randomUUID();
        when(portal.doctorsForTenant(tenantId)).thenReturn(List.of(
                new PatientPortalDoctorResponse("ravi", "Ravi Mehta", "Pediatrics", null, null, null),
                new PatientPortalDoctorResponse("amit", "Amit Verma", "General Medicine", null, null, null)));
        HealthcareAiReadOnlyToolGateway gateway = new HealthcareAiReadOnlyToolGateway(
                portal, mock(PublicCatalogFacade.class), mock(DiscoverReferenceDataService.class),
                mock(ClinicProfileService.class), mock(AppointmentService.class));

        var result = gateway.execute("FIND_DOCTOR",
                new HealthcareAiExecutionContext(tenantId, null, "GUEST_CALLER", HealthcareAiChannel.VOICE,
                        UUID.randomUUID(), null, null, "hi-IN"),
                new HealthcareAiReadOnlyToolGateway.ToolInput(null, "general physician", null, null, null, null));

        assertTrue(result.success());
        assertEquals(2, result.sourceCandidateCount());
        assertEquals(1, result.candidateCount());
        assertTrue(result.diagnostics().contains("specialty=General Medicine"));
        assertEquals("Amit Verma", ((com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse)
                ((List<?>) result.value()).getFirst()).doctorDisplayName());
    }

    @Test
    void voiceDoctorLookupWithoutFiltersReturnsAllTenantDoctors() {
        PatientPortalService portal = mock(PatientPortalService.class);
        UUID tenantId = UUID.randomUUID();
        when(portal.doctorsForTenant(tenantId)).thenReturn(List.of(
                new PatientPortalDoctorResponse("ravi", "Ravi Mehta", "Pediatrics", null, null, null),
                new PatientPortalDoctorResponse("amit", "Amit Verma", "General Medicine", null, null, null)));
        HealthcareAiReadOnlyToolGateway gateway = new HealthcareAiReadOnlyToolGateway(
                portal, mock(PublicCatalogFacade.class), mock(DiscoverReferenceDataService.class),
                mock(ClinicProfileService.class), mock(AppointmentService.class));

        var result = gateway.execute("FIND_DOCTOR",
                new HealthcareAiExecutionContext(tenantId, null, "GUEST_CALLER", HealthcareAiChannel.VOICE,
                        UUID.randomUUID(), null, null, "hi-IN"),
                new HealthcareAiReadOnlyToolGateway.ToolInput(null, null, null, null, null, null));

        assertTrue(result.success());
        assertEquals(2, result.sourceCandidateCount());
        assertEquals(2, result.candidateCount());
    }

    @Test
    void voiceDoctorLookupMatchesDevanagariNamesAgainstLatinDoctorRecords() {
        PatientPortalService portal = mock(PatientPortalService.class);
        UUID tenantId = UUID.randomUUID();
        when(portal.doctorsForTenant(tenantId)).thenReturn(List.of(
                new PatientPortalDoctorResponse("ravi", "Ravi Mehta", "Pediatrics", null, null, null),
                new PatientPortalDoctorResponse("amit", "Amit Verma", "General Medicine", null, null, null)));
        HealthcareAiReadOnlyToolGateway gateway = new HealthcareAiReadOnlyToolGateway(
                portal, mock(PublicCatalogFacade.class), mock(DiscoverReferenceDataService.class),
                mock(ClinicProfileService.class), mock(AppointmentService.class));
        var context = new HealthcareAiExecutionContext(tenantId, null, "GUEST_CALLER", HealthcareAiChannel.VOICE,
                UUID.randomUUID(), null, null, "hi-IN");

        var ravi = gateway.execute("FIND_DOCTOR", context,
                new HealthcareAiReadOnlyToolGateway.ToolInput("रवि मेहता", null, null, null, null, null));
        var amit = gateway.execute("FIND_DOCTOR", context,
                new HealthcareAiReadOnlyToolGateway.ToolInput("अमित वर्मा", null, null, null, null, null));

        assertEquals(1, ravi.candidateCount());
        assertEquals("Ravi Mehta", ((com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse)
                ((List<?>) ravi.value()).getFirst()).doctorDisplayName());
        assertEquals(1, amit.candidateCount());
        assertEquals("Amit Verma", ((com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse)
                ((List<?>) amit.value()).getFirst()).doctorDisplayName());
    }

    @Test
    void mutationAndUnknownToolsDefaultToDeny() {
        HealthcareAiReadOnlyToolGateway gateway = new HealthcareAiReadOnlyToolGateway(
                mock(PatientPortalService.class), mock(PublicCatalogFacade.class),
                mock(DiscoverReferenceDataService.class), mock(ClinicProfileService.class), mock(AppointmentService.class));
        HealthcareAiExecutionContext context = new HealthcareAiExecutionContext(
                UUID.randomUUID(), null, "GUEST_CALLER", HealthcareAiChannel.VOICE,
                null, null, null, "hi-IN");

        assertFalse(gateway.execute("BOOK_APPOINTMENT", context,
                HealthcareAiReadOnlyToolGateway.ToolInput.empty()).allowed());
        assertFalse(gateway.execute("UNKNOWN", context,
                HealthcareAiReadOnlyToolGateway.ToolInput.empty()).allowed());
    }

    @Test
    void recurringScheduleComesFromAppointmentAvailabilityEvenWhenNoSlotsAreFree() {
        PatientPortalService portal = mock(PatientPortalService.class);
        AppointmentService appointments = mock(AppointmentService.class);
        UUID tenantId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        when(portal.doctorsForTenant(tenantId)).thenReturn(List.of(
                new PatientPortalDoctorResponse(doctorId.toString(), "Ravi Mehta", "Pediatrics", null, null, null)));
        when(appointments.listDoctorAvailabilities(tenantId, doctorId)).thenReturn(List.of(
                new DoctorAvailabilityRecord(UUID.randomUUID(), tenantId, doctorId, "Ravi Mehta", DayOfWeek.MONDAY,
                        LocalTime.of(17, 0), LocalTime.of(20, 0), null, null, 30, 1, true, null, null)));
        HealthcareAiReadOnlyToolGateway gateway = gateway(portal, appointments);

        var result = gateway.execute("FIND_DOCTOR_SCHEDULE", voiceContext(tenantId),
                new HealthcareAiReadOnlyToolGateway.ToolInput("Ravi Mehta", null, null, null, null, null,
                        QueryType.RECURRING_SCHEDULE, Daypart.EVENING, ZoneId.of("Asia/Kolkata"), 0));

        var schedule = (HealthcareAiReadOnlyToolGateway.VoiceAvailabilityData) result.value();
        assertTrue(result.allowed());
        assertEquals("Ravi Mehta", schedule.doctorName());
        assertEquals(1, schedule.configuredScheduleWindows().size());
        assertTrue(schedule.bookableSlots().isEmpty());
        verify(appointments, never()).listSlots(any(), any(), any(), any());
    }

    @Test
    void effectiveAvailabilityUsesCanonicalDateSlotsAndFiltersByV2Daypart() {
        PatientPortalService portal = mock(PatientPortalService.class);
        AppointmentService appointments = mock(AppointmentService.class);
        UUID tenantId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 19);
        when(portal.doctorsForTenant(tenantId)).thenReturn(List.of(
                new PatientPortalDoctorResponse(doctorId.toString(), "Ravi Mehta", "Pediatrics", null, null, null)));
        when(appointments.listDoctorAvailabilities(tenantId, doctorId)).thenReturn(List.of(
                new DoctorAvailabilityRecord(UUID.randomUUID(), tenantId, doctorId, "Ravi Mehta", date.getDayOfWeek(),
                        LocalTime.of(17, 0), LocalTime.of(20, 0), null, null, 30, 1, true, null, null)));
        when(appointments.listUnavailability(tenantId, doctorId)).thenReturn(List.of());
        when(appointments.listSlots(tenantId, doctorId, date, ZoneId.of("Asia/Kolkata"))).thenReturn(List.of(
                slot(doctorId, date, LocalTime.of(17, 0), true),
                slot(doctorId, date, LocalTime.of(17, 30), false)));
        HealthcareAiReadOnlyToolGateway gateway = new HealthcareAiReadOnlyToolGateway(portal,
                mock(PublicCatalogFacade.class), mock(DiscoverReferenceDataService.class),
                mock(ClinicProfileService.class), appointments,
                Clock.fixed(Instant.parse("2026-10-19T06:00:00Z"), ZoneOffset.UTC));

        var result = gateway.execute("FIND_SLOTS", voiceContext(tenantId),
                new HealthcareAiReadOnlyToolGateway.ToolInput("Ravi Mehta", null, null, null, null, date,
                        QueryType.EFFECTIVE_DATE_AVAILABILITY, Daypart.EVENING, ZoneId.of("Asia/Kolkata"), 0));

        var availability = (HealthcareAiReadOnlyToolGateway.VoiceAvailabilityData) result.value();
        assertEquals(1, availability.configuredScheduleWindows().size());
        assertEquals(List.of(Daypart.EVENING), availability.availableDayparts());
        assertEquals(1, availability.bookableSlots().size());
        assertEquals(LocalTime.of(17, 0), availability.bookableSlots().getFirst().startTime());
        verify(appointments).listSlots(tenantId, doctorId, date, ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void effectiveScheduleUsesRecurringRowsAndDateExceptionWithoutInferringFromSlots() {
        PatientPortalService portal = mock(PatientPortalService.class);
        AppointmentService appointments = mock(AppointmentService.class);
        UUID tenantId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 20);
        when(portal.doctorsForTenant(tenantId)).thenReturn(List.of(
                new PatientPortalDoctorResponse(doctorId.toString(), "Ravi Mehta", "Pediatrics", null, null, null)));
        when(appointments.listDoctorAvailabilities(tenantId, doctorId)).thenReturn(List.of(
                new DoctorAvailabilityRecord(UUID.randomUUID(), tenantId, doctorId, "Ravi Mehta", DayOfWeek.TUESDAY,
                        LocalTime.of(17, 0), LocalTime.of(20, 0), null, null, 30, 1, true, null, null)));
        when(appointments.listUnavailability(tenantId, doctorId)).thenReturn(List.of(
                new DoctorUnavailabilityRecord(UUID.randomUUID(), tenantId, doctorId,
                        date.atTime(16, 0).atOffset(ZoneOffset.ofHoursMinutes(5, 30)),
                        date.atTime(21, 0).atOffset(ZoneOffset.ofHoursMinutes(5, 30)),
                        DoctorUnavailabilityType.LEAVE, null, true, null, null)));
        HealthcareAiReadOnlyToolGateway gateway = gateway(portal, appointments);

        var result = gateway.execute("FIND_DOCTOR_SCHEDULE", voiceContext(tenantId),
                new HealthcareAiReadOnlyToolGateway.ToolInput("Ravi Mehta", null, null, null, null, date,
                        QueryType.EFFECTIVE_DATE_SCHEDULE, Daypart.EVENING, ZoneId.of("Asia/Kolkata"), 0));

        var schedule = (HealthcareAiReadOnlyToolGateway.VoiceAvailabilityData) result.value();
        assertTrue(schedule.unavailableForDate());
        assertTrue(schedule.configuredScheduleWindows().isEmpty());
        verify(appointments, never()).listSlots(any(), any(), any(), any());
    }

    @Test
    void slotResultDistinguishesConfiguredScheduleFromFullyBookedAndPastOnlyRows() {
        PatientPortalService portal = mock(PatientPortalService.class);
        AppointmentService appointments = mock(AppointmentService.class);
        UUID tenantId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 20);
        when(portal.doctorsForTenant(tenantId)).thenReturn(List.of(
                new PatientPortalDoctorResponse(doctorId.toString(), "Ravi Mehta", "Pediatrics", null, null, null)));
        when(appointments.listDoctorAvailabilities(tenantId, doctorId)).thenReturn(List.of(
                new DoctorAvailabilityRecord(UUID.randomUUID(), tenantId, doctorId, "Ravi Mehta", DayOfWeek.TUESDAY,
                        LocalTime.of(17, 0), LocalTime.of(20, 0), null, null, 30, 1, true, null, null)));
        when(appointments.listUnavailability(tenantId, doctorId)).thenReturn(List.of());
        when(appointments.listSlots(tenantId, doctorId, date, ZoneId.of("Asia/Kolkata"))).thenReturn(List.of(
                slot(doctorId, date, LocalTime.of(17, 0), false)));
        HealthcareAiReadOnlyToolGateway gateway = gateway(portal, appointments);

        var result = gateway.execute("FIND_SLOTS", voiceContext(tenantId),
                new HealthcareAiReadOnlyToolGateway.ToolInput("Ravi Mehta", null, null, null, doctorId.toString(), date,
                        QueryType.SLOT_AVAILABILITY, Daypart.EVENING, ZoneId.of("Asia/Kolkata"), 0));

        var data = (HealthcareAiReadOnlyToolGateway.VoiceAvailabilityData) result.value();
        assertEquals(1, data.configuredScheduleWindows().size());
        assertTrue(data.fullSlotsOnly());
        assertFalse(data.unavailableForDate());
    }

    @Test
    void voiceNeverReturnsSlotAlreadyStartedInTenantLocalTime() {
        PatientPortalService portal = mock(PatientPortalService.class);
        AppointmentService appointments = mock(AppointmentService.class);
        UUID tenantId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 10, 19);
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T05:15:00Z"), ZoneOffset.UTC);
        when(portal.doctorsForTenant(tenantId)).thenReturn(List.of(
                new PatientPortalDoctorResponse(doctorId.toString(), "Ravi Mehta", "Pediatrics", null, null, null)));
        when(appointments.listDoctorAvailabilities(tenantId, doctorId)).thenReturn(List.of(
                new DoctorAvailabilityRecord(UUID.randomUUID(), tenantId, doctorId, "Ravi Mehta", DayOfWeek.MONDAY,
                        LocalTime.of(9, 0), LocalTime.of(12, 0), null, null, 30, 1, true, null, null)));
        when(appointments.listUnavailability(tenantId, doctorId)).thenReturn(List.of());
        when(appointments.listSlots(tenantId, doctorId, today, zone)).thenReturn(List.of(
                slot(doctorId, today, LocalTime.of(10, 30), true),
                slot(doctorId, today, LocalTime.of(11, 0), true),
                slot(doctorId, today, LocalTime.of(11, 30), true)));
        HealthcareAiReadOnlyToolGateway gateway = new HealthcareAiReadOnlyToolGateway(portal,
                mock(PublicCatalogFacade.class), mock(DiscoverReferenceDataService.class),
                mock(ClinicProfileService.class), appointments, clock);

        var result = gateway.execute("FIND_SLOTS", voiceContext(tenantId),
                new HealthcareAiReadOnlyToolGateway.ToolInput("Ravi Mehta", null, null, null, doctorId.toString(), today,
                        QueryType.SLOT_AVAILABILITY, Daypart.MORNING, zone, 0));
        var data = (HealthcareAiReadOnlyToolGateway.VoiceAvailabilityData) result.value();

        assertEquals(List.of(LocalTime.of(11, 0), LocalTime.of(11, 30)),
                data.bookableSlots().stream().map(HealthcareAiReadOnlyToolGateway.VoiceSlot::startTime).toList());
    }

    private static HealthcareAiReadOnlyToolGateway gateway(PatientPortalService portal, AppointmentService appointments) {
        return new HealthcareAiReadOnlyToolGateway(portal, mock(PublicCatalogFacade.class),
                mock(DiscoverReferenceDataService.class), mock(ClinicProfileService.class), appointments);
    }

    private static HealthcareAiExecutionContext voiceContext(UUID tenantId) {
        return new HealthcareAiExecutionContext(tenantId, null, "GUEST_CALLER", HealthcareAiChannel.VOICE,
                UUID.randomUUID(), null, null, "hi-IN");
    }

    private static DoctorAvailabilitySlotRecord slot(UUID doctorId, LocalDate date, LocalTime time, boolean bookable) {
        return new DoctorAvailabilitySlotRecord(doctorId, "Ravi Mehta", date, time, time.plusMinutes(30),
                bookable ? DoctorAvailabilitySlotStatus.AVAILABLE : DoctorAvailabilitySlotStatus.FULL,
                bookable ? 0 : 1, 1, bookable, bookable ? DoctorAvailabilitySlotTimeState.FUTURE
                : DoctorAvailabilitySlotTimeState.FUTURE, false, false, bookable,
                bookable ? null : "full", null, null, null, null, null, null, null);
    }
}
