package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotStatus;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotTimeState;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientSearchCriteria;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VoiceAppointmentWorkflowServiceTest {

    private VoiceAppointmentWorkflowService newService(
            AppointmentService appointmentService,
            PatientService patientService,
            TenantUserManagementService tenantUserManagementService
    ) {
        ClinicTimeZoneResolver clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        when(clinicTimeZoneResolver.resolve(any())).thenReturn(ZoneOffset.UTC);
        return new VoiceAppointmentWorkflowService(appointmentService, patientService, tenantUserManagementService, clinicTimeZoneResolver);
    }

    @Test
    void genericAppointmentIntentCollectsMissingFieldsWithoutBooking() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        VoiceAppointmentWorkflowService service = newService(appointmentService, patientService, tenantUserManagementService);

        VoiceWorkflowSummary summary = service.resolve(UUID.randomUUID(), "I want to book an appointment.", "en", null);

        assertThat(summary.mode()).isEqualTo("appointment-booking");
        assertThat(summary.bookingWorkflowState()).isEqualTo("COLLECTING_DETAILS");
        assertThat(summary.missingFields()).containsExactly("patientIdentity", "doctorName", "preferredDate", "preferredTimeWindow");
        assertThat(summary.nextPrompt()).contains("patient");
        assertThat(summary.booked()).isFalse();
        verify(appointmentService, never()).createScheduled(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void appointmentModeIdentifiesPatientAndDoctorSuggestsSlotWithoutBookingBeforeConfirmation() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        VoiceAppointmentWorkflowService service = newService(appointmentService, patientService, tenantUserManagementService);
        UUID tenantId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(patientService.search(eq(tenantId), any(PatientSearchCriteria.class))).thenReturn(List.of(patient(patientId, tenantId, "PAT-1001", "Manha", "Singh", "9876543210")));
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(doctor(doctorUserId, tenantId, "Dr Suresh Iyer")));
        when(appointmentService.listSlots(eq(tenantId), eq(doctorUserId), eq(tomorrow), any())).thenReturn(List.of(
                slot(doctorUserId, "Dr Suresh Iyer", tomorrow, LocalTime.of(9, 0), true),
                slot(doctorUserId, "Dr Suresh Iyer", tomorrow, LocalTime.of(9, 15), true)
        ));

        VoiceWorkflowSummary summary = service.resolve(
                tenantId,
                "Manha Singh 9876543210 wants Dr Suresh Iyer tomorrow morning for fever.",
                "en",
                null
        );

        assertThat(summary.patientId()).isEqualTo(patientId.toString());
        assertThat(summary.patientMatchStatus()).isEqualTo("IDENTIFIED");
        assertThat(summary.doctorUserId()).isEqualTo(doctorUserId.toString());
        assertThat(summary.doctorMatchStatus()).isEqualTo("IDENTIFIED");
        assertThat(summary.suggestedSlot()).isNotNull();
        assertThat(summary.suggestedSlot().slotTime()).isEqualTo("09:00");
        assertThat(summary.confirmationRequested()).isTrue();
        assertThat(summary.booked()).isFalse();
        verify(appointmentService).listSlots(eq(tenantId), eq(doctorUserId), eq(tomorrow), any());
        verify(appointmentService, never()).createScheduled(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void patientLookupRemainsTenantScopedAndAmbiguousWhenMultipleMatchesExist() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        VoiceAppointmentWorkflowService service = newService(appointmentService, patientService, tenantUserManagementService);
        UUID tenantId = UUID.randomUUID();

        when(patientService.search(eq(tenantId), any(PatientSearchCriteria.class))).thenReturn(List.of(
                patient(UUID.randomUUID(), tenantId, "PAT-1001", "Manha", "Singh", "9876543210"),
                patient(UUID.randomUUID(), tenantId, "PAT-1002", "Manha", "Sharma", "9876549999")
        ));

        VoiceWorkflowSummary summary = service.resolve(tenantId, "My name is Manha.", "en", null);

        verify(patientService).search(eq(tenantId), any(PatientSearchCriteria.class));
        assertThat(summary.patientMatchStatus()).isEqualTo("AMBIGUOUS");
        assertThat(summary.patientOptions()).hasSize(2);
        assertThat(summary.nextPrompt()).contains("multiple patients");
    }

    @Test
    void doctorLookupUsesTenantScopedDoctorsAndDoesNotFallbackToHardcodedDoctor() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        VoiceAppointmentWorkflowService service = newService(appointmentService, patientService, tenantUserManagementService);
        UUID tenantId = UUID.randomUUID();

        when(patientService.search(eq(tenantId), any(PatientSearchCriteria.class))).thenReturn(List.of(patient(UUID.randomUUID(), tenantId, "PAT-1001", "Manha", "Singh", "9876543210")));
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(
                doctor(UUID.randomUUID(), tenantId, "Dr Suresh Iyer"),
                doctor(UUID.randomUUID(), tenantId, "Dr Meera Rao")
        ));

        VoiceWorkflowSummary summary = service.resolve(tenantId, "Manha Singh 9876543210 wants Dr Unknown tomorrow morning.", "en", null);

        assertThat(summary.doctorUserId()).isNull();
        assertThat(summary.doctorMatchStatus()).isEqualTo("NOT_FOUND");
        assertThat(summary.doctorOptions()).contains("Dr Suresh Iyer", "Dr Meera Rao");
        assertThat(summary.nextPrompt()).contains("Available doctors include");
    }

    @Test
    void tenantACannotFindOrBookTenantBDoctor() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        VoiceAppointmentWorkflowService service = newService(appointmentService, patientService, tenantUserManagementService);
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        when(patientService.search(eq(tenantA), any(PatientSearchCriteria.class))).thenReturn(List.of(
                patient(UUID.randomUUID(), tenantA, "PAT-1001", "Manha", "Singh", "9876543210")
        ));
        when(tenantUserManagementService.list(tenantA)).thenReturn(List.of(
                doctor(UUID.randomUUID(), tenantA, "Dr Suresh Iyer")
        ));
        when(tenantUserManagementService.list(tenantB)).thenReturn(List.of(
                doctor(UUID.randomUUID(), tenantB, "Dr Arjun Menon")
        ));

        VoiceWorkflowSummary summary = service.resolve(
                tenantA,
                "Manha Singh 9876543210 wants Dr Arjun Menon tomorrow morning.",
                "en",
                null
        );

        verify(tenantUserManagementService).list(tenantA);
        verify(appointmentService, never()).listSlots(any(), any(), any());
        verify(appointmentService, never()).createScheduled(any(), any(), any(), any(Boolean.class));
        assertThat(summary.doctorMatchStatus()).isEqualTo("NOT_FOUND");
        assertThat(summary.doctorOptions()).contains("Dr Suresh Iyer");
        assertThat(summary.doctorOptions()).doesNotContain("Dr Arjun Menon");
        assertThat(summary.nextPrompt()).contains("Available doctors include");
    }

    @Test
    void confirmationCreatesAppointmentOnlyAfterExplicitConfirmation() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        VoiceAppointmentWorkflowService service = newService(appointmentService, patientService, tenantUserManagementService);
        UUID tenantId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(patientService.search(eq(tenantId), any(PatientSearchCriteria.class))).thenReturn(List.of(patient(patientId, tenantId, "PAT-1001", "Manha", "Singh", "9876543210")));
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(doctor(doctorUserId, tenantId, "Dr Suresh Iyer")));
        when(appointmentService.listSlots(eq(tenantId), eq(doctorUserId), eq(tomorrow), any())).thenReturn(List.of(slot(doctorUserId, "Dr Suresh Iyer", tomorrow, LocalTime.of(9, 0), true)));
        when(appointmentService.createScheduled(eq(tenantId), any(AppointmentUpsertCommand.class), eq(null), eq(false), any()))
                .thenReturn(new AppointmentRecord(
                        appointmentId,
                        tenantId,
                        patientId,
                        "PAT-1001",
                        "Manha Singh",
                        "9876543210",
                        doctorUserId,
                        "Dr Suresh Iyer",
                        null,
                        tomorrow,
                        LocalTime.of(9, 0),
                        null,
                        "fever",
                        AppointmentType.SCHEDULED,
                        AppointmentPriority.NORMAL,
                        AppointmentStatus.BOOKED,
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                ));

        VoiceWorkflowSummary collected = service.resolve(tenantId, "Manha Singh 9876543210 wants Dr Suresh Iyer tomorrow morning for fever.", "en", null);
        verify(appointmentService, never()).createScheduled(any(), any(), any(), any(Boolean.class));

        VoiceWorkflowSummary confirmed = service.resolve(tenantId, "Yes, book it.", "en", collected);

        ArgumentCaptor<AppointmentUpsertCommand> commandCaptor = ArgumentCaptor.forClass(AppointmentUpsertCommand.class);
        verify(appointmentService).createScheduled(eq(tenantId), commandCaptor.capture(), eq(null), eq(false), any());
        AppointmentUpsertCommand command = commandCaptor.getValue();
        assertThat(command.patientId()).isEqualTo(patientId);
        assertThat(command.doctorUserId()).isEqualTo(doctorUserId);
        assertThat(command.appointmentDate()).isEqualTo(tomorrow);
        assertThat(command.appointmentTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(confirmed.booked()).isTrue();
        assertThat(confirmed.bookedAppointmentId()).isEqualTo(appointmentId.toString());
        assertThat(confirmed.confirmationRequested()).isFalse();
        assertThat(confirmed.intentState()).isEqualTo("BOOKED");
    }

    @Test
    void slotAvailabilityFallsBackToNearestAvailableSlots() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        VoiceAppointmentWorkflowService service = newService(appointmentService, patientService, tenantUserManagementService);
        UUID tenantId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(patientService.search(eq(tenantId), any(PatientSearchCriteria.class))).thenReturn(List.of(patient(patientId, tenantId, "PAT-1001", "Manha", "Singh", "9876543210")));
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(doctor(doctorUserId, tenantId, "Dr Suresh Iyer")));
        when(appointmentService.listSlots(eq(tenantId), eq(doctorUserId), eq(tomorrow), any())).thenReturn(List.of(
                slot(doctorUserId, "Dr Suresh Iyer", tomorrow, LocalTime.of(9, 0), true),
                slot(doctorUserId, "Dr Suresh Iyer", tomorrow, LocalTime.of(10, 0), true),
                slot(doctorUserId, "Dr Suresh Iyer", tomorrow, LocalTime.of(11, 0), true)
        ));

        VoiceWorkflowSummary summary = service.resolve(tenantId, "Manha Singh 9876543210 wants Dr Suresh Iyer tomorrow at 08:30.", "en", null);

        assertThat(summary.suggestedSlot()).isNotNull();
        assertThat(summary.suggestedSlot().slotTime()).isEqualTo("09:00");
        assertThat(summary.slotSuggestions()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(summary.confirmationRequested()).isTrue();
    }

    @Test
    void repeatedResolutionFailureTriggersHandoff() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        VoiceAppointmentWorkflowService service = newService(appointmentService, patientService, tenantUserManagementService);
        UUID tenantId = UUID.randomUUID();

        VoiceWorkflowSummary first = service.resolve(tenantId, "book appointment", "en", null);
        VoiceWorkflowSummary second = service.resolve(tenantId, "book appointment", "en", first);
        VoiceWorkflowSummary third = service.resolve(tenantId, "book appointment", "en", second);
        VoiceWorkflowSummary fourth = service.resolve(tenantId, "book appointment", "en", third);

        assertThat(fourth.handoffRequired()).isTrue();
        assertThat(fourth.handoffReason()).isEqualTo("repeated-resolution-failure");
        assertThat(fourth.nextPrompt()).isEqualTo("I’ll ask the receptionist to help you with this booking.");
    }

    @Test
    void hindiPromptAndConfirmationStayHindiAware() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        TenantUserManagementService tenantUserManagementService = mock(TenantUserManagementService.class);
        VoiceAppointmentWorkflowService service = newService(appointmentService, patientService, tenantUserManagementService);
        UUID tenantId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(patientService.search(eq(tenantId), any(PatientSearchCriteria.class))).thenReturn(List.of(patient(patientId, tenantId, "PAT-1001", "मनहा", "सिंह", "9876543210")));
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(doctor(doctorUserId, tenantId, "Dr Suresh Iyer")));
        when(appointmentService.listSlots(eq(tenantId), eq(doctorUserId), eq(tomorrow), any())).thenReturn(List.of(slot(doctorUserId, "Dr Suresh Iyer", tomorrow, LocalTime.of(9, 0), true)));
        when(appointmentService.createScheduled(eq(tenantId), any(AppointmentUpsertCommand.class), eq(null), eq(false), any()))
                .thenReturn(new AppointmentRecord(
                        UUID.randomUUID(),
                        tenantId,
                        patientId,
                        "PAT-1001",
                        "मनहा सिंह",
                        "9876543210",
                        doctorUserId,
                        "Dr Suresh Iyer",
                        null,
                        tomorrow,
                        LocalTime.of(9, 0),
                        null,
                        null,
                        AppointmentType.SCHEDULED,
                        AppointmentPriority.NORMAL,
                        AppointmentStatus.BOOKED,
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                ));

        VoiceWorkflowSummary collected = service.resolve(tenantId, "मेरा नाम मनहा सिंह है। डॉक्टर Suresh Iyer, कल सुबह।", "auto", null);
        assertThat(collected.language()).isEqualTo("hi");
        assertThat(collected.nextPrompt()).contains("क्या मैं");

        VoiceWorkflowSummary confirmed = service.resolve(tenantId, "ठीक है, बुक कर दीजिए", "auto", collected);
        assertThat(confirmed.booked()).isTrue();
        assertThat(confirmed.nextPrompt()).contains("बुक हो गई");
    }

    private PatientRecord patient(UUID id, UUID tenantId, String patientNumber, String firstName, String lastName, String mobile) {
        return new PatientRecord(
                id,
                tenantId,
                patientNumber,
                firstName,
                lastName,
                PatientGender.UNKNOWN,
                null,
                null,
                mobile,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private TenantUserRecord doctor(UUID id, UUID tenantId, String displayName) {
        return new TenantUserRecord(id, tenantId, "sub-" + id, displayName.toLowerCase().replace(' ', '.') + "@clinic.test", displayName, "ACTIVE", "DOCTOR", "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now(), "READY");
    }

    private DoctorAvailabilitySlotRecord slot(UUID doctorUserId, String doctorName, LocalDate date, LocalTime time, boolean selectable) {
        return new DoctorAvailabilitySlotRecord(
                doctorUserId,
                doctorName,
                date,
                time,
                time.plusMinutes(15),
                selectable ? DoctorAvailabilitySlotStatus.AVAILABLE : DoctorAvailabilitySlotStatus.FULL,
                selectable ? 0 : 1,
                1,
                selectable,
                DoctorAvailabilitySlotTimeState.FUTURE,
                false,
                false,
                selectable,
                null,
                null,
                null,
                null,
                null,
                null,
                AppointmentStatus.BOOKED,
                null
        );
    }
}
