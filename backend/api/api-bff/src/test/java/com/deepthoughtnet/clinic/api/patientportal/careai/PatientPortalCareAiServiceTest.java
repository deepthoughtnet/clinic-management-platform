package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private static final UUID TENANT_B = UUID.randomUUID();
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
    void appointmentIsNotBookedBeforeExplicitConfirmation() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-a", "Dr Suresh", "Cardiology")));
        when(patientPortalService.doctorSlots("doctor-a", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(9, 0), true),
                slot(tomorrow, LocalTime.of(9, 30), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Suresh tomorrow morning for fever.",
                "en"
        ));

        assertThat(response.state().confirmationPending()).isTrue();
        assertThat(response.state().booked()).isFalse();
        assertThat(response.assistantMessage()).contains("Should I book this slot?");
        verify(patientPortalService, never()).bookAppointment(any());
    }

    @Test
    void greetingPromptsForDoctorOrSpecialityWithoutFailing() {
        var response = service.message(new PatientPortalCareAiMessageRequest("Hello", "en"));

        assertThat(response.assistantMessage()).contains("help you book an appointment");
        assertThat(response.state().booked()).isFalse();
        assertThat(response.state().handoffRequired()).isFalse();
        verify(patientPortalService, never()).bookAppointment(any());
    }

    @Test
    void bookingIntentAsksForDoctorOrSpeciality() {
        var response = service.message(new PatientPortalCareAiMessageRequest("I want to book an appointment.", "en"));

        assertThat(response.assistantMessage()).contains("Which speciality or doctor");
        assertThat(response.state().booked()).isFalse();
        verify(patientPortalService, never()).bookAppointment(any());
    }

    @Test
    void appointmentIsBookedAfterExplicitConfirmation() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-a", "Dr Suresh", "Cardiology")));
        when(patientPortalService.doctorSlots("doctor-a", tomorrow)).thenReturn(List.of(slot(tomorrow, LocalTime.of(9, 0), true)));
        when(patientPortalService.bookAppointment(any())).thenReturn(new PatientPortalAppointmentConfirmationResponse(
                tomorrow,
                LocalTime.of(9, 0),
                "Dr Suresh",
                "Sunrise Clinic",
                "PATIENT_PORTAL",
                "BOOKED",
                "fever",
                "Appointment booked successfully."
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Suresh tomorrow morning for fever.", "en"));
        var confirmed = service.message(new PatientPortalCareAiMessageRequest("Yes, please confirm it.", "en"));

        ArgumentCaptor<PatientPortalAppointmentBookingRequest> captor = ArgumentCaptor.forClass(PatientPortalAppointmentBookingRequest.class);
        verify(patientPortalService).bookAppointment(captor.capture());
        assertThat(captor.getValue().publicDoctorId()).isEqualTo("doctor-a");
        assertThat(captor.getValue().appointmentDate()).isEqualTo(tomorrow);
        assertThat(captor.getValue().appointmentTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(captor.getValue().reason()).contains("fever");
        assertThat(confirmed.state().booked()).isTrue();
        assertThat(confirmed.state().bookingStatus()).isEqualTo("BOOKED");
    }

    @Test
    void specialitySelectionThenDoctorAndSlotCompletesBookingFlow() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(
                doctor("doctor-a", "Dr A", "Cardiologist"),
                doctor("doctor-b", "Dr B", "Cardiologist")
        ));
        when(patientPortalService.doctorSlots("doctor-a", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(10, 0), true),
                slot(tomorrow, LocalTime.of(10, 30), true),
                slot(tomorrow, LocalTime.of(11, 0), true)
        ));
        when(patientPortalService.bookAppointment(any())).thenReturn(new PatientPortalAppointmentConfirmationResponse(
                tomorrow,
                LocalTime.of(10, 30),
                "Dr A",
                "Sunrise Clinic",
                "PATIENT_PORTAL",
                "BOOKED",
                null,
                "Appointment booked successfully."
        ));

        var speciality = service.message(new PatientPortalCareAiMessageRequest("Cardiologist", "en"));
        assertThat(speciality.assistantMessage()).contains("Please choose one");
        assertThat(speciality.state().doctorOptions()).containsExactly("Dr A · Cardiologist", "Dr B · Cardiologist");

        var doctorAndTime = service.message(new PatientPortalCareAiMessageRequest("Dr A tomorrow morning", "en"));
        assertThat(doctorAndTime.assistantMessage()).contains("Should I book this slot?");
        assertThat(doctorAndTime.state().slotOptions()).contains("10:00", "10:30", "11:00");

        var exactSlot = service.message(new PatientPortalCareAiMessageRequest("10:30", "en"));
        assertThat(exactSlot.state().suggestedSlot()).isEqualTo("10:30");
        assertThat(exactSlot.state().confirmationPending()).isTrue();

        var confirmed = service.message(new PatientPortalCareAiMessageRequest("Yes", "en"));
        assertThat(confirmed.state().booked()).isTrue();
        verify(patientPortalService).bookAppointment(any());
    }

    @Test
    void bookingUsesVerifiedSessionContextNotUserProvidedPatientIdentity() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-a", "Dr Suresh", "Cardiology")));
        when(patientPortalService.doctorSlots("doctor-a", tomorrow)).thenReturn(List.of(slot(tomorrow, LocalTime.of(9, 0), true)));
        when(patientPortalService.bookAppointment(any())).thenReturn(new PatientPortalAppointmentConfirmationResponse(
                tomorrow,
                LocalTime.of(9, 0),
                "Dr Suresh",
                "Sunrise Clinic",
                "PATIENT_PORTAL",
                "BOOKED",
                "fever",
                "Appointment booked successfully."
        ));

        service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Suresh tomorrow morning for patient 99999 because fever.",
                "en"
        ));
        service.message(new PatientPortalCareAiMessageRequest("confirm", "en"));

        ArgumentCaptor<PatientPortalAppointmentBookingRequest> captor = ArgumentCaptor.forClass(PatientPortalAppointmentBookingRequest.class);
        verify(patientPortalService).bookAppointment(captor.capture());
        assertThat(captor.getValue().reason()).contains("fever");
        assertThat(captor.getValue().reason()).doesNotContain("99999");
    }

    @Test
    void tenantAPatientCannotBookTenantBDoctor() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-a", "Dr Suresh", "Cardiology")));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Arjun Menon tomorrow morning.",
                "en"
        ));

        assertThat(response.state().booked()).isFalse();
        assertThat(response.state().doctorOptions()).contains("Dr Suresh · Cardiology");
        assertThat(response.state().doctorOptions()).doesNotContain("Dr Arjun Menon");
        verify(patientPortalService, never()).doctorSlots("doctor-b", tomorrow);
        verify(patientPortalService, never()).bookAppointment(any());
    }

    @Test
    void unavailableSlotIsRejectedAfterConfirmationAttempt() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-a", "Dr Suresh", "Cardiology")));
        when(patientPortalService.doctorSlots("doctor-a", tomorrow)).thenReturn(List.of(slot(tomorrow, LocalTime.of(9, 0), true)));
        when(patientPortalService.bookAppointment(any())).thenThrow(new IllegalArgumentException("Selected slot is no longer available."));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Suresh tomorrow morning.", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("Yes", "en"));

        assertThat(response.state().booked()).isFalse();
        assertThat(response.state().confirmationPending()).isFalse();
        assertThat(response.assistantMessage()).contains("Selected slot is no longer available");
        verify(patientPortalService, times(1)).bookAppointment(any());
    }

    @Test
    void doctorAmbiguityAsksForClarification() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(
                doctor("doctor-a", "Dr Suresh Iyer", "Cardiology"),
                doctor("doctor-b", "Dr Suresh Menon", "Cardiology")
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Suresh tomorrow morning.",
                "en"
        ));

        assertThat(response.assistantMessage()).contains("multiple matching doctors");
        assertThat(response.state().doctorOptions()).containsExactly(
                "Dr Suresh Iyer · Cardiology",
                "Dr Suresh Menon · Cardiology"
        );
        verify(patientPortalService, never()).bookAppointment(any());
    }

    @Test
    void repeatedResolutionFailureTriggersHandoff() {
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-a", "Dr Suresh", "Cardiology")));

        service.message(new PatientPortalCareAiMessageRequest("book appointment", "en"));
        service.message(new PatientPortalCareAiMessageRequest("book appointment", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("book appointment", "en"));

        assertThat(response.state().handoffRequired()).isTrue();
        assertThat(response.state().handoffReason()).isEqualTo("repeated-resolution-failure");
        assertThat(response.assistantMessage()).contains("receptionist");
        verify(patientPortalService, never()).bookAppointment(any());
    }

    @Test
    void sessionStateRemainsTenantScoped() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-a", "Dr Suresh", "Cardiology")));
        when(patientPortalService.doctorSlots("doctor-a", tomorrow)).thenReturn(List.of(slot(tomorrow, LocalTime.of(9, 0), true)));

        var tenantA = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Suresh tomorrow morning.",
                "en"
        ));

        setPatientContext(TENANT_B, APP_USER_A);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-b", "Dr Meera", "Dermatology")));
        var tenantB = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment tomorrow morning.",
                "en"
        ));

        assertThat(tenantA.state().doctorName()).isEqualTo("Dr Suresh");
        assertThat(tenantB.state().doctorName()).isNull();
        assertThat(tenantB.state().doctorOptions()).contains("Dr Meera · Dermatology");
    }

    private static void setPatientContext(UUID tenantId, UUID appUserId) {
        RequestContextHolder.set(new RequestContext(new TenantId(tenantId), appUserId, "patient-sub", Set.of("PATIENT"), "PATIENT", "corr-careai"));
    }

    private static PatientPortalDoctorResponse doctor(String id, String name, String specialization) {
        return new PatientPortalDoctorResponse(id, name, specialization, null, null, null);
    }

    private static PatientPortalDoctorSlotResponse slot(LocalDate date, LocalTime time, boolean selectable) {
        return new PatientPortalDoctorSlotResponse(date, time, null, selectable ? "AVAILABLE" : "FULL", selectable);
    }
}
