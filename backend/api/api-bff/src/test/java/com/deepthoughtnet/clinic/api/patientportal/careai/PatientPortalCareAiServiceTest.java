package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PatientPortalCareAiServiceTest {
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID APP_USER_A = UUID.randomUUID();

    private PatientPortalService patientPortalService;
    private PatientPortalCareAiService service;

    @BeforeEach
    void setUp() {
        patientPortalService = mock(PatientPortalService.class);
        service = new PatientPortalCareAiService(patientPortalService);
        setPatientContext(TENANT_A, APP_USER_A);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void doctorSelectionByNumberPersistsAcrossMessages() {
        when(patientPortalService.doctors()).thenReturn(List.of(
                doctor("doctor-1", "Dr Ashish Shri", "Cardiology"),
                doctor("doctor-2", "Dr Neha Mehta", "Cardiology"),
                doctor("doctor-3", "Dr Suresh Iyer", "Cardiology")
        ));

        var first = service.message(new PatientPortalCareAiMessageRequest("Book appointment with cardiology", "en"));

        assertThat(first.assistantMessage()).contains("multiple matching doctors");
        assertThat(first.state().doctorOptions()).containsExactly(
                "Dr Ashish Shri · Cardiology",
                "Dr Neha Mehta · Cardiology",
                "Dr Suresh Iyer · Cardiology"
        );

        var second = service.message(new PatientPortalCareAiMessageRequest("2", "en"));

        assertThat(second.state().doctorName()).isEqualTo("Dr Neha Mehta");
        assertThat(second.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(second.assistantMessage()).contains("What date would you prefer");
    }

    @Test
    void doctorSelectionByPartialNameChoosesSingleDoctor() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(
                doctor("doctor-1", "Dr Ashish Shri", "Dermatology"),
                doctor("doctor-2", "Dr Neha Mehta", "Dermatology")
        ));
        when(patientPortalService.doctorSlots("doctor-2", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(10, 0), true),
                slot(tomorrow, LocalTime.of(10, 30), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Neha tomorrow morning",
                "en"
        ));

        assertThat(response.state().doctorName()).isEqualTo("Dr Neha Mehta");
        assertThat(response.assistantMessage()).contains("Please choose a slot");
        assertThat(response.state().slotOptions()).containsExactly("10:00", "10:30");
    }

    @Test
    void bookingFlowRequiresExplicitSlotAndConfirmation() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(9, 0), true),
                slot(tomorrow, LocalTime.of(9, 30), true),
                slot(tomorrow, LocalTime.of(10, 0), true)
        ));
        when(patientPortalService.bookAppointment(any())).thenReturn(new PatientPortalAppointmentConfirmationResponse(
                tomorrow,
                LocalTime.of(9, 30),
                "Dr Neha Mehta",
                "Sunrise Clinic",
                "Scheduled",
                "BOOKED",
                "fever",
                "Appointment booked successfully."
        ));

        var first = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow morning for fever",
                "en"
        ));
        assertThat(first.state().confirmationPending()).isFalse();
        assertThat(first.state().slotOptions()).containsExactly("09:00", "09:30", "10:00");

        var second = service.message(new PatientPortalCareAiMessageRequest("2", "en"));
        assertThat(second.state().suggestedSlot()).isEqualTo("09:30");
        assertThat(second.state().confirmationPending()).isTrue();

        var third = service.message(new PatientPortalCareAiMessageRequest("yes", "en"));
        ArgumentCaptor<PatientPortalAppointmentBookingRequest> captor = ArgumentCaptor.forClass(PatientPortalAppointmentBookingRequest.class);
        verify(patientPortalService).bookAppointment(captor.capture());
        assertThat(captor.getValue().publicDoctorId()).isEqualTo("doctor-neha");
        assertThat(captor.getValue().appointmentDate()).isEqualTo(tomorrow);
        assertThat(captor.getValue().appointmentTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(third.state().booked()).isTrue();
        assertThat(third.state().actionCompleted()).isTrue();
        assertThat(third.state().lastAction()).isEqualTo("BOOK_APPOINTMENT");
    }

    @Test
    void rescheduleFlowSelectsAppointmentThenSlotThenConfirms() {
        UUID appointmentId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        LocalDate currentDate = LocalDate.now().plusDays(2);
        LocalDate newDate = LocalDate.now().plusDays(4);
        when(patientPortalService.careAiUpcomingAppointments()).thenReturn(List.of(
                appointment(appointmentId, doctorUserId, "Dr Neha Mehta", currentDate, LocalTime.of(11, 0), "BOOKED")
        ));
        when(patientPortalService.doctorSlots(doctorUserId.toString(), newDate)).thenReturn(List.of(
                slot(newDate, LocalTime.of(12, 0), true),
                slot(newDate, LocalTime.of(12, 30), true)
        ));
        when(patientPortalService.rescheduleAppointment(appointmentId, newDate, LocalTime.of(12, 30), "Follow-up review"))
                .thenReturn(new PatientPortalAppointmentConfirmationResponse(
                        newDate,
                        LocalTime.of(12, 30),
                        "Dr Neha Mehta",
                        "Sunrise Clinic",
                        "Scheduled",
                        "BOOKED",
                        "Follow-up review",
                        "Appointment rescheduled successfully."
                ));

        var first = service.message(new PatientPortalCareAiMessageRequest("Reschedule my appointment", "en"));
        assertThat(first.assistantMessage()).contains("What new date would you prefer");

        var second = service.message(new PatientPortalCareAiMessageRequest("2026-06-08 12:30", "en"));
        assertThat(second.state().suggestedSlot()).isEqualTo("12:30");
        assertThat(second.state().confirmationPending()).isTrue();

        var third = service.message(new PatientPortalCareAiMessageRequest("confirm", "en"));
        verify(patientPortalService).rescheduleAppointment(appointmentId, newDate, LocalTime.of(12, 30), "Follow-up review");
        assertThat(third.state().actionCompleted()).isTrue();
        assertThat(third.state().lastAction()).isEqualTo("RESCHEDULE_APPOINTMENT");
        assertThat(third.assistantMessage()).contains("rescheduled successfully");
    }

    @Test
    void cancellationFlowRequiresAppointmentSelectionAndConfirmation() {
        UUID appointmentA = UUID.randomUUID();
        UUID appointmentB = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(1);
        when(patientPortalService.careAiUpcomingAppointments()).thenReturn(List.of(
                appointment(appointmentA, doctorUserId, "Dr Ashish Shri", date, LocalTime.of(9, 0), "BOOKED"),
                appointment(appointmentB, doctorUserId, "Dr Neha Mehta", date.plusDays(1), LocalTime.of(10, 0), "BOOKED")
        ));
        when(patientPortalService.cancelAppointment(appointmentB, "Cancelled via CareAI"))
                .thenReturn(new PatientPortalAppointmentConfirmationResponse(
                        date.plusDays(1),
                        LocalTime.of(10, 0),
                        "Dr Neha Mehta",
                        "Sunrise Clinic",
                        "Scheduled",
                        "CANCELLED",
                        "Review visit",
                        "Appointment cancelled successfully."
                ));

        var first = service.message(new PatientPortalCareAiMessageRequest("Cancel my appointment", "en"));
        assertThat(first.assistantMessage()).contains("Please choose which appointment");

        var second = service.message(new PatientPortalCareAiMessageRequest("2", "en"));
        assertThat(second.state().confirmationPending()).isTrue();
        assertThat(second.state().selectedAppointment()).contains("Dr Neha Mehta");

        var third = service.message(new PatientPortalCareAiMessageRequest("yes", "en"));
        verify(patientPortalService).cancelAppointment(appointmentB, "Cancelled via CareAI");
        assertThat(third.state().lastAction()).isEqualTo("CANCEL_APPOINTMENT");
        assertThat(third.state().bookingStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void appointmentStatusReturnsNextAppointmentDetails() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.careAiUpcomingAppointments()).thenReturn(List.of(
                appointment(UUID.randomUUID(), UUID.randomUUID(), "Dr Neha Mehta", tomorrow, LocalTime.of(14, 0), "BOOKED")
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest("When is my next appointment?", "en"));

        assertThat(response.state().currentIntent()).isEqualTo("APPOINTMENT_STATUS");
        assertThat(response.assistantMessage()).contains("Dr Neha Mehta");
        assertThat(response.assistantMessage()).contains("Sunrise Clinic");
        assertThat(response.assistantMessage()).contains("14:00");
    }

    @Test
    void resetClearsConversationState() {
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-1", "Dr Ashish Shri", "Cardiology")));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Ashish Shri", "en"));
        var reset = service.reset();
        var afterReset = service.message(new PatientPortalCareAiMessageRequest("2", "en"));

        assertThat(reset.cleared()).isTrue();
        assertThat(afterReset.state().currentIntent()).isNull();
        assertThat(afterReset.assistantMessage()).contains("What would you like to do");
    }

    @Test
    void tenantIsolationDoesNotExposeUnknownDoctors() {
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-1", "Dr Ashish Shri", "Cardiology")));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow morning",
                "en"
        ));

        assertThat(response.state().doctorOptions()).contains("Dr Ashish Shri · Cardiology");
        assertThat(response.state().doctorOptions()).doesNotContain("Dr Neha Mehta");
        verify(patientPortalService, never()).bookAppointment(any());
    }

    private PatientPortalDoctorResponse doctor(String publicDoctorId, String doctorName, String specialization) {
        return new PatientPortalDoctorResponse(publicDoctorId, doctorName, specialization, "MBBS", "Room 1", 8);
    }

    private PatientPortalDoctorSlotResponse slot(LocalDate date, LocalTime time, boolean selectable) {
        return new PatientPortalDoctorSlotResponse(
                date,
                time,
                time.plusMinutes(15),
                selectable ? "AVAILABLE" : "FULL",
                selectable
        );
    }

    private PatientPortalCareAiAppointmentOption appointment(
            UUID appointmentId,
            UUID doctorUserId,
            String doctorName,
            LocalDate date,
            LocalTime time,
            String status
    ) {
        return new PatientPortalCareAiAppointmentOption(
                appointmentId,
                doctorUserId,
                doctorName,
                "Sunrise Clinic",
                date,
                time,
                status,
                "Follow-up review"
        );
    }

    private void setPatientContext(UUID tenantId, UUID appUserId) {
        RequestContextHolder.set(new RequestContext(new TenantId(tenantId), appUserId, "subject-1", Set.of("PATIENT"), "PATIENT", "corr-1"));
    }
}
