package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotStatus;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VoiceAppointmentWorkflowServiceTest {

    @Test
    void appointmentModeAsksForDoctorDateAndTimeBeforeIdentity() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        VoiceAppointmentWorkflowService service = new VoiceAppointmentWorkflowService(appointmentService, patientService);

        VoiceWorkflowSummary summary = service.resolve(UUID.randomUUID(), "I want to book an appointment.", "en", null);

        assertThat(summary.mode()).isEqualTo("appointment-booking");
        assertThat(summary.intentState()).isEqualTo("COLLECTING_DETAILS");
        assertThat(summary.missingFields()).containsExactly("doctorName", "preferredDate", "preferredTimeWindow", "patientIdentity");
        assertThat(summary.nextPrompt()).contains("doctor");
        assertThat(summary.confirmationRequested()).isFalse();
        verify(appointmentService, never()).listSlots(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void appointmentModeRetainsCollectedStateAndSuggestsSlotBeforeConfirmation() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        VoiceAppointmentWorkflowService service = new VoiceAppointmentWorkflowService(appointmentService, patientService);
        UUID tenantId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        when(appointmentService.listAvailabilities(tenantId)).thenReturn(List.of(
                new DoctorAvailabilityRecord(
                        UUID.randomUUID(),
                        tenantId,
                        doctorUserId,
                        "Dr ABC",
                        DayOfWeek.from(tomorrow),
                        LocalTime.of(9, 0),
                        LocalTime.of(13, 0),
                        null,
                        null,
                        15,
                        1,
                        true,
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )
        ));
        when(appointmentService.listSlots(tenantId, doctorUserId, tomorrow)).thenReturn(List.of(
                new DoctorAvailabilitySlotRecord(
                        doctorUserId,
                        "Dr ABC",
                        tomorrow,
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 15),
                        DoctorAvailabilitySlotStatus.AVAILABLE,
                        0,
                        1,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        AppointmentStatus.BOOKED,
                        null
                )
        ));

        VoiceWorkflowSummary firstTurn = service.resolve(tenantId, "I want to book an appointment.", "en", null);
        VoiceWorkflowSummary secondTurn = service.resolve(tenantId, "Tomorrow morning with Dr ABC.", "en", firstTurn);

        assertThat(secondTurn.doctorName()).isEqualTo("Dr ABC");
        assertThat(secondTurn.doctorUserId()).isEqualTo(doctorUserId.toString());
        assertThat(secondTurn.preferredDate()).isEqualTo(tomorrow.toString());
        assertThat(secondTurn.preferredTimeWindow()).isEqualTo("morning");
        assertThat(secondTurn.suggestedSlot()).isNotNull();
        assertThat(secondTurn.suggestedSlot().slotTime()).isEqualTo("09:00");
        assertThat(secondTurn.confirmationRequested()).isTrue();
        assertThat(secondTurn.bookingConfirmed()).isFalse();
        assertThat(secondTurn.nextPrompt()).contains("Ask only for confirmation");
        verify(appointmentService).listSlots(tenantId, doctorUserId, tomorrow);
    }

    @Test
    void hindiAppointmentPromptRemainsHindiAware() {
        AppointmentService appointmentService = mock(AppointmentService.class);
        PatientService patientService = mock(PatientService.class);
        VoiceAppointmentWorkflowService service = new VoiceAppointmentWorkflowService(appointmentService, patientService);

        VoiceWorkflowSummary summary = service.resolve(UUID.randomUUID(), "मुझे अपॉइंटमेंट बुक करनी है।", "hi", null);

        assertThat(summary.language()).isEqualTo("hi");
        assertThat(summary.nextPrompt()).contains("डॉक्टर");
    }
}
