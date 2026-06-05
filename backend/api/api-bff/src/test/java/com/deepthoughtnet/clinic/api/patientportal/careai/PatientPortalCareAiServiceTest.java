package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
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
    private AiOrchestrationService aiOrchestrationService;
    private VoiceTestProperties voiceTestProperties;
    private PatientPortalCareAiService service;

    @BeforeEach
    void setUp() {
        patientPortalService = mock(PatientPortalService.class);
        aiOrchestrationService = mock(AiOrchestrationService.class);
        voiceTestProperties = new VoiceTestProperties();
        voiceTestProperties.getLlm().setMaxOutputTokens(1024);
        PatientPortalCareAiPlanner planner = new LlmBackedPatientPortalCareAiPlanner(
                aiOrchestrationService,
                new ObjectMapper(),
                voiceTestProperties,
                true
        );
        service = new PatientPortalCareAiService(
                patientPortalService,
                planner
        );
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
    void absoluteDateDayMonthYearIsRecognized() {
        LocalDate targetDate = LocalDate.now().plusDays(30);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", targetDate)).thenReturn(List.of(
                slot(targetDate, LocalTime.of(10, 0), true),
                slot(targetDate, LocalTime.of(10, 30), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta on "
                        + targetDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")),
                "en"
        ));

        assertThat(response.state().preferredDate()).isEqualTo(targetDate.toString());
        assertThat(response.assistantMessage()).contains("Please choose a slot");
    }

    @Test
    void absoluteDateMonthDayYearIsRecognized() {
        LocalDate targetDate = LocalDate.now().plusDays(31);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", targetDate)).thenReturn(List.of(
                slot(targetDate, LocalTime.of(16, 0), true),
                slot(targetDate, LocalTime.of(16, 30), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta on "
                        + targetDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d uuuu"))
                        + " at 4 PM",
                "en"
        ));

        assertThat(response.state().preferredDate()).isEqualTo(targetDate.toString());
        assertThat(response.state().suggestedSlot()).isEqualTo("16:00");
        assertThat(response.state().confirmationPending()).isTrue();
    }

    @Test
    void nextFridayIsRecognizedForReschedule() {
        UUID appointmentId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        LocalDate currentDate = LocalDate.now().plusDays(1);
        LocalDate nextFriday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.FRIDAY));
        when(patientPortalService.careAiUpcomingAppointments()).thenReturn(List.of(
                appointment(appointmentId, doctorUserId, "Dr Neha Mehta", currentDate, LocalTime.of(11, 0), "BOOKED")
        ));
        when(patientPortalService.doctorSlots(doctorUserId.toString(), nextFriday)).thenReturn(List.of(
                slot(nextFriday, LocalTime.of(14, 0), true),
                slot(nextFriday, LocalTime.of(15, 0), true)
        ));

        var first = service.message(new PatientPortalCareAiMessageRequest("Reschedule my appointment", "en"));
        assertThat(first.assistantMessage()).contains("What new date would you prefer");

        var second = service.message(new PatientPortalCareAiMessageRequest("next Friday afternoon", "en"));

        assertThat(second.state().preferredDate()).isEqualTo(nextFriday.toString());
        assertThat(second.state().preferredTimeWindow()).isEqualTo("afternoon");
        assertThat(second.state().slotOptions()).containsExactly("14:00", "15:00");
    }

    @Test
    void thisSaturdayIsRecognized() {
        LocalDate saturday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", saturday)).thenReturn(List.of(
                slot(saturday, LocalTime.of(9, 0), true),
                slot(saturday, LocalTime.of(10, 0), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta this Saturday",
                "en"
        ));

        assertThat(response.state().preferredDate()).isEqualTo(saturday.toString());
        assertThat(response.state().slotOptions()).containsExactly("09:00", "10:00");
    }

    @Test
    void tomorrowMorningBiasesSlotSuggestions() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(9, 0), true),
                slot(tomorrow, LocalTime.of(10, 30), true),
                slot(tomorrow, LocalTime.of(14, 0), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow morning",
                "en"
        ));

        assertThat(response.state().preferredDate()).isEqualTo(tomorrow.toString());
        assertThat(response.state().preferredTimeWindow()).isEqualTo("morning");
        assertThat(response.state().slotOptions()).containsExactly("09:00", "10:30");
    }

    @Test
    void afterLunchBiasesSlotSuggestions() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(10, 30), true),
                slot(tomorrow, LocalTime.of(12, 30), true),
                slot(tomorrow, LocalTime.of(15, 0), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow after lunch",
                "en"
        ));

        assertThat(response.state().preferredTimeWindow()).isEqualTo("afternoon");
        assertThat(response.state().slotOptions()).containsExactly("12:30", "15:00");
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

        var second = service.message(new PatientPortalCareAiMessageRequest(newDate + " 12:30", "en"));
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
    void invalidDateIsRejectedPolitely() {
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta on 31/02/2026",
                "en"
        ));

        assertThat(response.state().preferredDate()).isNull();
        assertThat(response.assistantMessage()).contains("could not understand that date");
    }

    @Test
    void pastDateIsRejectedPolitely() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta on " + pastDate.format(java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu")),
                "en"
        ));

        assertThat(response.state().preferredDate()).isNull();
        assertThat(response.assistantMessage()).contains("Please choose today or a future date");
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
    void waitingForTimeAndBookAtSevenPmProgressesToNearestSlots() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(18, 30), true),
                slot(tomorrow, LocalTime.of(19, 15), true),
                slot(tomorrow, LocalTime.of(20, 0), true)
        ));

        service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow",
                "en"
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest("book at 7 p.m.", "en"));

        assertThat(response.state().preferredTimeWindow()).isEqualTo("19:00");
        assertThat(response.state().slotOptions()).containsExactly("19:15", "18:30", "20:00");
        assertThat(response.assistantMessage()).contains("No exact slot is available at 19:00");
    }

    @Test
    void waitingForTimeAndCheckSlotInEveningProgresses() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(17, 0), true),
                slot(tomorrow, LocalTime.of(18, 0), true)
        ));

        service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow",
                "en"
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest("Check the slot in evening", "en"));

        assertThat(response.state().preferredTimeWindow()).isEqualTo("evening");
        assertThat(response.state().slotOptions()).containsExactly("17:00", "18:00");
        assertThat(response.assistantMessage()).contains("Please choose a slot");
    }

    @Test
    void waitingForTimeAndSwitchTopicClearsBookingFlow() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));

        service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow",
                "en"
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest("Can we switch topic and talk about something else?", "en"));

        assertThat(response.state().currentIntent()).isNull();
        assertThat(response.state().doctorName()).isNull();
        assertThat(response.state().preferredDate()).isNull();
        assertThat(response.assistantMessage()).contains("cleared the current booking flow");
    }

    @Test
    void doesNotRepeatSameTimePromptThirdTime() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of());

        service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow",
                "en"
        ));
        var first = service.message(new PatientPortalCareAiMessageRequest("some time", "en"));
        var second = service.message(new PatientPortalCareAiMessageRequest("later", "en"));
        var third = service.message(new PatientPortalCareAiMessageRequest("whenever", "en"));

        assertThat(first.assistantMessage()).isEqualTo("What time works best, such as morning, afternoon, evening, night, before lunch, after lunch, or a specific time?");
        assertThat(second.assistantMessage()).contains("I still need a time preference");
        assertThat(third.assistantMessage()).contains("I still need a time preference");
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

    @Test
    void aiOrchestrationHelpsWhenNaturalLanguageNeedsStructuredExtraction() {
        LocalDate targetDate = LocalDate.now().plusDays(32);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(aiOrchestrationService.complete(any())).thenReturn(aiResponse("""
                {
                  "intent": "BOOK_APPOINTMENT",
                  "doctorName": "Dr Neha Mehta",
                  "preferredDate": "%s",
                  "preferredTimeWindow": "morning"
                }
                """.formatted(targetDate)));
        when(patientPortalService.doctorSlots("doctor-neha", targetDate)).thenReturn(List.of(
                slot(targetDate, LocalTime.of(9, 0), true),
                slot(targetDate, LocalTime.of(10, 0), true),
                slot(targetDate, LocalTime.of(15, 0), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "I want to book appointment with Neha Mehta on 4 Jun morning",
                "en"
        ));

        verify(aiOrchestrationService).complete(argThat(request ->
                request.taskType() == AiTaskType.GENERIC_EXTRACTION
                        && "generic.extraction.v1".equals(request.promptTemplateCode())
                        && request.tenantId().equals(TENANT_A)
                        && request.actorUserId().equals(APP_USER_A)
                        && request.maxTokens() == 1024
                        && Double.compare(request.temperature(), 0.2d) == 0
        ));
        assertThat(response.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(response.state().doctorName()).isEqualTo("Dr Neha Mehta");
        assertThat(response.state().preferredDate()).isEqualTo(targetDate.toString());
        assertThat(response.state().preferredTimeWindow()).isEqualTo("morning");
        assertThat(response.state().slotOptions()).containsExactly("09:00", "10:00");
    }

    @Test
    void plannerReceivesConversationStateAndAvailableActions() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(aiOrchestrationService.complete(any()))
                .thenReturn(aiResponse("""
                        {
                          "intent": "BOOK_APPOINTMENT"
                        }
                        """))
                .thenReturn(aiResponse("""
                        {
                          "preferredTimeWindow": "19:00"
                        }
                        """));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(18, 30), true),
                slot(tomorrow, LocalTime.of(19, 30), true)
        ));

        service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow",
                "en"
        ));
        service.message(new PatientPortalCareAiMessageRequest("around 7 pm", "en"));

        verify(aiOrchestrationService).complete(argThat(request -> {
            Map<String, Object> inputs = request.inputVariables();
            return request.taskType() == AiTaskType.GENERIC_EXTRACTION
                    && "BOOK_APPOINTMENT".equals(inputs.get("currentIntent"))
                    && ((List<?>) inputs.get("missingFields")).contains("slot")
                    && ((List<?>) inputs.get("slotOptions")).contains("18:30")
                    && ((List<?>) inputs.get("availableActions")).contains("EXTRACT_TIME");
        }));
    }

    @Test
    void plannerCanClearBookingFlowOnSemanticTopicSwitch() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(aiOrchestrationService.complete(any()))
                .thenReturn(aiResponse("""
                        {
                          "intent": "BOOK_APPOINTMENT"
                        }
                        """))
                .thenReturn(aiResponse("""
                        {
                          "topicSwitch": true
                        }
                        """));

        service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow",
                "en"
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Let's discuss something else instead",
                "en"
        ));

        assertThat(response.state().currentIntent()).isNull();
        assertThat(response.assistantMessage()).contains("cleared the current booking flow");
    }

    @Test
    void deterministicParserStillWorksWhenAiOrchestrationFails() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(aiOrchestrationService.complete(any())).thenThrow(new IllegalStateException("provider down"));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(9, 0), true),
                slot(tomorrow, LocalTime.of(10, 30), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow morning",
                "en"
        ));

        assertThat(response.state().doctorName()).isEqualTo("Dr Neha Mehta");
        assertThat(response.state().preferredDate()).isEqualTo(tomorrow.toString());
        assertThat(response.state().slotOptions()).containsExactly("09:00", "10:30");
    }

    @Test
    void truncatedAiExtractionFallsBackSafelyToDeterministicParser() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(aiOrchestrationService.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiProductCode.GENERIC,
                AiTaskType.GENERIC_EXTRACTION,
                "gemini",
                "gemini-2.5-flash",
                "Sorry, I missed that. Could you please repeat?",
                "{\"answer\":\"Sorry, I missed that. Could you please repeat?\"}",
                BigDecimal.valueOf(0.42),
                List.of(),
                List.of(),
                List.of("AI returned partially structured output. Please verify."),
                null,
                18L,
                false,
                null
        ));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(9, 0), true),
                slot(tomorrow, LocalTime.of(10, 30), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow morning",
                "en"
        ));

        assertThat(response.state().doctorName()).isEqualTo("Dr Neha Mehta");
        assertThat(response.state().preferredDate()).isEqualTo(tomorrow.toString());
        assertThat(response.state().preferredTimeWindow()).isEqualTo("morning");
        assertThat(response.state().slotOptions()).containsExactly("09:00", "10:30");
    }

    @Test
    void invalidAiExtractionDoesNotEraseDeterministicTimeFields() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(aiOrchestrationService.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiProductCode.GENERIC,
                AiTaskType.GENERIC_EXTRACTION,
                "gemini",
                "gemini-2.5-flash",
                "{\"answer\":\"Sorry\"",
                null,
                BigDecimal.valueOf(0.22),
                List.of(),
                List.of(),
                List.of("partial"),
                null,
                8L,
                false,
                null
        ));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(18, 0), true),
                slot(tomorrow, LocalTime.of(19, 0), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow evening",
                "en"
        ));

        assertThat(response.state().doctorName()).isEqualTo("Dr Neha Mehta");
        assertThat(response.state().preferredDate()).isEqualTo(tomorrow.toString());
        assertThat(response.state().preferredTimeWindow()).isEqualTo("evening");
        assertThat(response.state().slotOptions()).containsExactly("18:00", "19:00");
    }

    @Test
    void nonActionableAiPayloadDoesNotOverrideDeterministicParser() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(aiOrchestrationService.complete(any())).thenReturn(aiResponse("""
                {
                  "answer": "No external model was called."
                }
                """));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(9, 0), true),
                slot(tomorrow, LocalTime.of(10, 30), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow morning",
                "en"
        ));

        assertThat(response.state().doctorName()).isEqualTo("Dr Neha Mehta");
        assertThat(response.state().preferredDate()).isEqualTo(tomorrow.toString());
        assertThat(response.state().preferredTimeWindow()).isEqualTo("morning");
        assertThat(response.state().slotOptions()).containsExactly("09:00", "10:30");
    }

    @Test
    void aiInterpretationCanUseJsonOutputTextWhenStructuredJsonIsAbsent() {
        LocalDate targetDate = LocalDate.now().plusDays(33);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(aiOrchestrationService.complete(any())).thenReturn(new AiOrchestrationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiProductCode.GENERIC,
                AiTaskType.GENERIC_EXTRACTION,
                "gemini",
                "gemini-2.5-flash",
                """
                {
                  "intent": "BOOK_APPOINTMENT",
                  "doctorName": "Dr Neha Mehta",
                  "preferredDate": "%s",
                  "preferredTimeWindow": "morning"
                }
                """.formatted(targetDate),
                null,
                BigDecimal.valueOf(0.91),
                List.of(),
                List.of(),
                List.of(),
                null,
                21L,
                false,
                null
        ));
        when(patientPortalService.doctorSlots("doctor-neha", targetDate)).thenReturn(List.of(
                slot(targetDate, LocalTime.of(9, 0), true),
                slot(targetDate, LocalTime.of(10, 0), true)
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest(
                "I want to book appointment with Neha Mehta on 4 Jun morning",
                "en"
        ));

        assertThat(response.state().doctorName()).isEqualTo("Dr Neha Mehta");
        assertThat(response.state().preferredDate()).isEqualTo(targetDate.toString());
        assertThat(response.state().preferredTimeWindow()).isEqualTo("morning");
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

    private AiOrchestrationResponse aiResponse(String structuredJson) {
        return new AiOrchestrationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiProductCode.GENERIC,
                AiTaskType.GENERIC_EXTRACTION,
                "mock",
                "mock-model",
                "structured extraction",
                structuredJson,
                BigDecimal.ONE,
                List.of(),
                List.of(),
                List.of(),
                null,
                12L,
                false,
                null
        );
    }
}
