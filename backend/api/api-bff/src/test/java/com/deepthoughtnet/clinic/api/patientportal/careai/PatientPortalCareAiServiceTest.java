package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.careai.CareAiTaskNotificationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiChannel;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationSessionSnapshot;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationTurnCommand;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiWorkflowState;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiWorkflowType;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowEntity;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskPriority;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskService;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskUpsertResult;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEntity;
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
import java.time.ZoneId;
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
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    private PatientPortalService patientPortalService;
    private AiOrchestrationService aiOrchestrationService;
    private CareAiConversationPersistenceService conversationPersistenceService;
    private CareAiReceptionistTaskService receptionistTaskService;
    private CareAiTaskNotificationService taskNotificationService;
    private ClinicTimeZoneResolver clinicTimeZoneResolver;
    private VoiceTestProperties voiceTestProperties;
    private PatientPortalCareAiService service;

    @BeforeEach
    void setUp() {
        patientPortalService = mock(PatientPortalService.class);
        aiOrchestrationService = mock(AiOrchestrationService.class);
        conversationPersistenceService = mock(CareAiConversationPersistenceService.class);
        receptionistTaskService = mock(CareAiReceptionistTaskService.class);
        taskNotificationService = mock(CareAiTaskNotificationService.class);
        clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
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
                clinicTimeZoneResolver,
                planner,
                conversationPersistenceService,
                receptionistTaskService,
                taskNotificationService
        );
        when(patientPortalService.currentPatientId()).thenReturn(UUID.randomUUID());
        when(clinicTimeZoneResolver.resolve(any())).thenReturn(ZoneId.of("Asia/Kolkata"));
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
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
        assertThat(third.state().slotOptions()).isEmpty();
        assertThat(third.state().confirmationPending()).isFalse();

        ArgumentCaptor<CareAiConversationTurnCommand> persistenceCaptor = ArgumentCaptor.forClass(CareAiConversationTurnCommand.class);
        verify(conversationPersistenceService, org.mockito.Mockito.atLeast(3)).safeRecordTurn(persistenceCaptor.capture());
        CareAiConversationTurnCommand lastTurn = persistenceCaptor.getAllValues().getLast();
        assertThat(lastTurn.workflowSnapshot()).isNotNull();
        assertThat(lastTurn.workflowSnapshot().state()).isEqualTo(CareAiWorkflowState.COMPLETED);
    }

    @Test
    void afterBookingSuccessThankYouByeDoesNotReuseSlotMenu() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(16, 0), true),
                slot(tomorrow, LocalTime.of(16, 30), true),
                slot(tomorrow, LocalTime.of(17, 0), true)
        ));
        when(patientPortalService.bookAppointment(any())).thenReturn(new PatientPortalAppointmentConfirmationResponse(
                tomorrow,
                LocalTime.of(16, 30),
                "Dr Neha Mehta",
                "Sunrise Clinic",
                "Scheduled",
                "BOOKED",
                "fever",
                "Appointment booked successfully."
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta tomorrow", "en"));
        service.message(new PatientPortalCareAiMessageRequest("2", "en"));
        var booked = service.message(new PatientPortalCareAiMessageRequest("yes", "en"));
        var afterThanks = service.message(new PatientPortalCareAiMessageRequest("Thank you. Have a nice day. Bye", "en"));

        assertThat(booked.state().slotOptions()).isEmpty();
        assertThat(afterThanks.assistantMessage()).doesNotContain("Please choose a slot");
        assertThat(afterThanks.assistantMessage()).doesNotContain("16:00");
        assertThat(afterThanks.assistantMessage()).contains("Have a nice day");
        assertThat(afterThanks.state().slotOptions()).isEmpty();
        assertThat(afterThanks.state().confirmationPending()).isFalse();
    }

    @Test
    void newBookingRequestAfterCompletionStartsCleanly() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        LocalDate dayAfterTomorrow = LocalDate.now(CLINIC_ZONE).plusDays(2);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(16, 0), true),
                slot(tomorrow, LocalTime.of(16, 30), true),
                slot(tomorrow, LocalTime.of(17, 0), true)
        ));
        when(patientPortalService.doctorSlots("doctor-neha", dayAfterTomorrow)).thenReturn(List.of(
                slot(dayAfterTomorrow, LocalTime.of(9, 0), true),
                slot(dayAfterTomorrow, LocalTime.of(9, 30), true)
        ));
        when(patientPortalService.bookAppointment(any())).thenReturn(new PatientPortalAppointmentConfirmationResponse(
                tomorrow,
                LocalTime.of(16, 30),
                "Dr Neha Mehta",
                "Sunrise Clinic",
                "Scheduled",
                "BOOKED",
                "fever",
                "Appointment booked successfully."
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta tomorrow", "en"));
        service.message(new PatientPortalCareAiMessageRequest("2", "en"));
        service.message(new PatientPortalCareAiMessageRequest("yes", "en"));

        var newRequest = service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta on " + dayAfterTomorrow.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")),
                "en"
        ));

        assertThat(newRequest.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(newRequest.state().preferredDate()).isEqualTo(dayAfterTomorrow.toString());
        assertThat(newRequest.state().slotOptions()).containsExactly("09:00", "09:30");
        assertThat(newRequest.assistantMessage()).doesNotContain("Appointment booked successfully.");
    }

    @Test
    void humanHandoffPhraseCreatesReceptionistTask() {
        when(receptionistTaskService.upsertHandoffTask(any(), any())).thenReturn(new CareAiReceptionistTaskUpsertResult(receptionistTask("HUMAN_HANDOFF"), true));

        var response = service.message(new PatientPortalCareAiMessageRequest("I want to talk to receptionist", "en"));

        assertThat(response.assistantMessage()).contains("I’ve created a request for our receptionist");
        verify(receptionistTaskService).upsertHandoffTask(any(), org.mockito.Mockito.eq(CareAiReceptionistTaskPriority.MEDIUM));
        verify(patientPortalService, never()).bookAppointment(any());
    }

    @Test
    void callbackPhraseCreatesCallbackTask() {
        when(receptionistTaskService.upsertCallbackTask(any(), any())).thenReturn(new CareAiReceptionistTaskUpsertResult(receptionistTask("CALLBACK_REQUEST"), true));

        var response = service.message(new PatientPortalCareAiMessageRequest("Please call me back tomorrow evening", "en"));

        assertThat(response.assistantMessage()).contains("callback request");
        assertThat(response.assistantMessage()).contains("tomorrow evening");
        verify(receptionistTaskService).upsertCallbackTask(any(), org.mockito.Mockito.eq(CareAiReceptionistTaskPriority.MEDIUM));
        verify(patientPortalService, never()).bookAppointment(any());
    }

    @Test
    void bookingFlowHandoffCreatesAppointmentHandoffTask() {
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-ashish", "Dr Ashish Shri", "General Medicine")));
        when(receptionistTaskService.upsertAppointmentHandoffTask(any(), any()))
                .thenReturn(new CareAiReceptionistTaskUpsertResult(receptionistTask("APPOINTMENT_HANDOFF"), true));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Ashish Shri", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("I want to talk to receptionist", "en"));

        assertThat(response.assistantMessage()).contains("I’ve created a request for our receptionist");
        verify(receptionistTaskService).upsertAppointmentHandoffTask(any(), org.mockito.Mockito.eq(CareAiReceptionistTaskPriority.MEDIUM));
        verify(receptionistTaskService, never()).upsertHandoffTask(any(), any());
    }

    @Test
    void missingInMemoryStateRehydratesFromPersistedWorkflowContext() {
        UUID patientId = UUID.randomUUID();
        when(patientPortalService.currentPatientId()).thenReturn(patientId);
        LocalDate targetDate = LocalDate.now(CLINIC_ZONE).plusDays(1);
        CareAiConversationEntity conversation = CareAiConversationEntity.create(
                TENANT_A,
                "PATIENT_PORTAL_VOICE",
                patientId,
                null,
                "resume-voice-1"
        );
        CareAiWorkflowEntity workflow = CareAiWorkflowEntity.create(
                TENANT_A,
                conversation.getId(),
                "BOOK_APPOINTMENT",
                "COLLECTING_INFO",
                """
                {
                  "intent":"BOOK_APPOINTMENT",
                  "doctorId":"doctor-neha",
                  "doctorName":"Dr Neha Mehta",
                  "preferredDate":"%s",
                  "preferredTimeWindow":"evening",
                  "slotPromptLead":"Please choose a slot by number or time:",
                  "slotChoices":[
                    {"appointmentDate":"%s","slotTime":"17:00"},
                    {"appointmentDate":"%s","slotTime":"17:30"}
                  ],
                  "slotOptions":["17:00","17:30"],
                  "answeredState":{"doctor":true,"date":true,"timePreference":true,"slot":false,"confirmation":false}
                }
                """.formatted(targetDate, targetDate, targetDate),
                "choose-slot",
                0
        );
        when(conversationPersistenceService.findLatestSessionSnapshot(
                TENANT_A,
                CareAiChannel.PATIENT_PORTAL_VOICE,
                patientId,
                "resume-voice-1",
                8
        )).thenReturn(new CareAiConversationSessionSnapshot(conversation, workflow, null, List.of()));

        setPatientContext(TENANT_A, APP_USER_A, "resume-voice-1");
        var response = service.messageFromVoice(new PatientPortalCareAiMessageRequest("2", "en"));

        assertThat(response.state().doctorName()).isEqualTo("Dr Neha Mehta");
        assertThat(response.state().preferredDate()).isEqualTo(targetDate.toString());
        assertThat(response.state().preferredTimeWindow()).isEqualTo("evening");
        assertThat(response.state().suggestedSlot()).isEqualTo("17:30");
        assertThat(response.state().confirmationPending()).isTrue();
        assertThat(response.assistantMessage()).contains("Should I book");
    }

    @Test
    void absoluteDateDayMonthYearIsRecognized() {
        LocalDate targetDate = LocalDate.now(CLINIC_ZONE).plusDays(30);
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
        LocalDate targetDate = LocalDate.now(CLINIC_ZONE).plusDays(31);
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
    void dateOnlyReplyDayMonthYearProgressesToTimeQuestion() {
        LocalDate targetDate = nextDate(6, 11);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", targetDate)).thenReturn(List.of());

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest(
                targetDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")),
                "en"
        ));

        assertThat(response.state().preferredDate()).isEqualTo(targetDate.toString());
        assertThat(response.state().confirmationPending()).isFalse();
        assertThat(response.assistantMessage()).doesNotContain("What date would you prefer");
        assertThat(response.assistantMessage()).contains("time");
    }

    @Test
    void dateOnlyReplyMonthDayWithoutYearProgressesToTimeQuestion() {
        LocalDate targetDate = nextDate(6, 11);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", targetDate)).thenReturn(List.of());

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("June 11", "en"));

        assertThat(response.state().preferredDate()).isEqualTo(targetDate.toString());
        assertThat(response.assistantMessage()).doesNotContain("What date would you prefer");
        assertThat(response.assistantMessage()).contains("time");
    }

    @Test
    void embeddedDateReplyProgressesToTimeQuestion() {
        LocalDate targetDate = nextDate(6, 8);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", targetDate)).thenReturn(List.of());

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest(
                "Please book for " + targetDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")),
                "en"
        ));

        assertThat(response.state().preferredDate()).isEqualTo(targetDate.toString());
        assertThat(response.assistantMessage()).doesNotContain("What date would you prefer");
        assertThat(response.assistantMessage()).contains("time");
    }

    @Test
    void dateOnlyReplyWithAnotherAbsoluteDateProgressesToTimeQuestion() {
        LocalDate targetDate = nextDate(6, 9);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", targetDate)).thenReturn(List.of());

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest(
                targetDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")),
                "en"
        ));

        assertThat(response.state().preferredDate()).isEqualTo(targetDate.toString());
        assertThat(response.assistantMessage()).doesNotContain("Please tell me the date");
        assertThat(response.assistantMessage()).contains("time");
    }

    @Test
    void nextFridayIsRecognizedForReschedule() {
        UUID appointmentId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        LocalDate currentDate = LocalDate.now(CLINIC_ZONE).plusDays(1);
        LocalDate nextFriday = LocalDate.now(CLINIC_ZONE).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.FRIDAY));
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
        LocalDate saturday = LocalDate.now(CLINIC_ZONE).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
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
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
        LocalDate currentDate = LocalDate.now(CLINIC_ZONE).plusDays(2);
        LocalDate newDate = LocalDate.now(CLINIC_ZONE).plusDays(4);
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
        LocalDate date = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
        LocalDate pastDate = LocalDate.now(CLINIC_ZONE).minusDays(1);
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
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
    void eveningWithoutNearestOptionsDoesNotEndWithEmptyColonPrompt() {
        LocalDate tuesday = LocalDate.now(CLINIC_ZONE).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
        if (!tuesday.isAfter(LocalDate.now(CLINIC_ZONE))) {
            tuesday = tuesday.plusWeeks(1);
        }
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tuesday)).thenReturn(List.of());

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        service.message(new PatientPortalCareAiMessageRequest("Tuesday", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("Evening", "en"));

        assertThat(response.assistantMessage()).doesNotEndWith(":");
        assertThat(response.assistantMessage()).doesNotContain("Here are the nearest available options:");
        assertThat(response.assistantMessage()).contains("Would you like morning, afternoon, night, or another date?");
    }

    @Test
    void eveningWithoutMatchingSlotsRendersNearestAvailableOptions() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(9, 0), true),
                slot(tomorrow, LocalTime.of(9, 30), true),
                slot(tomorrow, LocalTime.of(10, 0), true)
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        service.message(new PatientPortalCareAiMessageRequest("tomorrow", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("Evening", "en"));

        assertThat(response.assistantMessage()).contains("Here are the nearest available options:");
        assertThat(response.assistantMessage()).contains("1. 09:00");
        assertThat(response.assistantMessage()).contains("2. 09:30");
        assertThat(response.assistantMessage()).contains("3. 10:00");
        assertThat(response.state().slotOptions()).containsExactly("09:00", "09:30", "10:00");
    }

    @Test
    void cantSeeTheOptionsRerendersStoredSlotChoices() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(9, 0), true),
                slot(tomorrow, LocalTime.of(9, 30), true),
                slot(tomorrow, LocalTime.of(10, 0), true)
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        service.message(new PatientPortalCareAiMessageRequest("tomorrow", "en"));
        service.message(new PatientPortalCareAiMessageRequest("Evening", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("I can't see the options", "en"));

        assertThat(response.assistantMessage()).contains("1. 09:00");
        assertThat(response.assistantMessage()).contains("2. 09:30");
        assertThat(response.assistantMessage()).contains("3. 10:00");
    }

    @Test
    void correctedDoctorNamePhraseFindsDrNeha() {
        when(patientPortalService.doctors()).thenReturn(List.of(
                doctor("doctor-ashish", "Dr Ashish Shri", "General Medicine"),
                doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")
        ));

        service.message(new PatientPortalCareAiMessageRequest("Can you book a appointment?", "en"));
        service.message(new PatientPortalCareAiMessageRequest("They want to see Dr. Deha", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("Dr. Nam, name is Dr. Neha", "en"));

        assertThat(response.assistantMessage()).doesNotContain("Please tell me the doctor name or speciality you want.");
        assertThat(response.assistantMessage()).contains("Did you mean Dr Neha Mehta?");
        assertThat(response.state().doctorOptions()).containsExactly("Dr Neha Mehta · General Medicine");
    }

    @Test
    void waitingForTimeAndSwitchTopicAsksForClarification() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));

        service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow",
                "en"
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest("Can we switch topic and talk about something else?", "en"));

        assertThat(response.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(response.state().doctorName()).isEqualTo("Dr Neha Mehta");
        assertThat(response.state().preferredDate()).isEqualTo(tomorrow.toString());
        assertThat(response.assistantMessage()).contains("cancel this booking flow, or ask something else");
    }

    @Test
    void doesNotRepeatSameTimePromptThirdTime() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of());

        service.message(new PatientPortalCareAiMessageRequest(
                "Book appointment with Dr Neha Mehta tomorrow",
                "en"
        ));
        var first = service.message(new PatientPortalCareAiMessageRequest("some time", "en"));
        var second = service.message(new PatientPortalCareAiMessageRequest("later", "en"));
        var third = service.message(new PatientPortalCareAiMessageRequest("whenever", "en"));

        assertThat(first.assistantMessage()).contains("Please tell me the time preference more clearly");
        assertThat(second.assistantMessage()).doesNotContain("Please choose a time preference");
        assertThat(third.assistantMessage()).doesNotContain("Please choose a time preference");
        assertThat(third.assistantMessage()).contains("checking");
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
        LocalDate targetDate = LocalDate.now(CLINIC_ZONE).plusDays(32);
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
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
    void plannerCanRequestTopicSwitchClarification() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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

        assertThat(response.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(response.assistantMessage()).contains("cancel this booking flow, or ask something else");
    }

    @Test
    void deterministicParserStillWorksWhenAiOrchestrationFails() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
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
        LocalDate targetDate = LocalDate.now(CLINIC_ZONE).plusDays(33);
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

    @Test
    void chatTurnPersistsConversationAndMessages() {
        when(patientPortalService.doctors()).thenReturn(List.of(
                doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));

        ArgumentCaptor<CareAiConversationTurnCommand> captor = ArgumentCaptor.forClass(CareAiConversationTurnCommand.class);
        verify(conversationPersistenceService).safeRecordTurn(captor.capture());
        assertThat(captor.getValue().channel().name()).isEqualTo("PATIENT_PORTAL_CHAT");
        assertThat(captor.getValue().userMessage()).isEqualTo("Book appointment with Dr Neha Mehta");
        assertThat(captor.getValue().assistantMessage()).contains("date");
    }

    @Test
    void bookingWorkflowPersistenceTransitionsFromConfirmationToCompleted() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(9, 30), true)
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

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta tomorrow morning", "en"));
        service.message(new PatientPortalCareAiMessageRequest("1", "en"));
        service.message(new PatientPortalCareAiMessageRequest("yes", "en"));

        ArgumentCaptor<CareAiConversationTurnCommand> captor = ArgumentCaptor.forClass(CareAiConversationTurnCommand.class);
        verify(conversationPersistenceService, org.mockito.Mockito.atLeast(3)).safeRecordTurn(captor.capture());
        List<CareAiConversationTurnCommand> commands = captor.getAllValues();
        CareAiConversationTurnCommand confirmationTurn = commands.get(commands.size() - 2);
        CareAiConversationTurnCommand completionTurn = commands.getLast();
        assertThat(confirmationTurn.workflowSnapshot().workflowType()).isEqualTo(CareAiWorkflowType.BOOK_APPOINTMENT);
        assertThat(confirmationTurn.workflowSnapshot().state()).isEqualTo(CareAiWorkflowState.WAITING_CONFIRMATION);
        assertThat(completionTurn.workflowSnapshot().state()).isEqualTo(CareAiWorkflowState.COMPLETED);
        assertThat(completionTurn.conversationStatus().name()).isEqualTo("COMPLETED");
    }

    @Test
    void persistenceFailureDoesNotBreakCareAiResponse() {
        when(patientPortalService.doctors()).thenReturn(List.of(
                doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")
        ));
        doThrow(new IllegalStateException("db down")).when(conversationPersistenceService).safeRecordTurn(any());

        var response = service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));

        assertThat(response.assistantMessage()).contains("date");
    }

    @Test
    void bookInEveningDoesNotRepeatSameTimeQuestion() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(18, 0), true),
                slot(tomorrow, LocalTime.of(18, 30), true)
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        service.message(new PatientPortalCareAiMessageRequest(tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")), "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("Book in evening, three", "en"));

        assertThat(response.assistantMessage()).doesNotContain("What time works best");
        assertThat(response.assistantMessage()).doesNotContain("Please choose a time preference");
        assertThat(response.state().preferredTimeWindow()).isEqualTo("evening");
        assertThat(response.state().slotOptions()).containsExactly("18:00", "18:30");
    }

    @Test
    void goodMorningDoesNotSetTimePreference() {
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("Good morning", "en"));

        assertThat(response.state().preferredTimeWindow()).isNull();
        assertThat(response.assistantMessage()).doesNotContain("Please choose a time preference");
    }

    @Test
    void optionThreeCapturesEveningTimePreference() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(18, 0), true),
                slot(tomorrow, LocalTime.of(18, 30), true)
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        service.message(new PatientPortalCareAiMessageRequest(tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")), "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("option three", "en"));

        assertThat(response.state().preferredTimeWindow()).isEqualTo("evening");
        assertThat(response.assistantMessage()).contains("18:00");
        assertThat(response.assistantMessage()).doesNotContain("Please choose a time preference");
    }

    @Test
    void numericThreeCapturesEveningWhenLastQuestionWasTimePreference() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(18, 0), true),
                slot(tomorrow, LocalTime.of(18, 30), true)
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        service.message(new PatientPortalCareAiMessageRequest(tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")), "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("3", "en"));

        assertThat(response.state().preferredTimeWindow()).isEqualTo("evening");
        assertThat(response.state().slotOptions()).containsExactly("18:00", "18:30");
        assertThat(response.assistantMessage()).doesNotContain("Please choose a time preference");
    }

    @Test
    void dateAndTimePreferenceArePersistedInWorkflowContext() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(18, 0), true),
                slot(tomorrow, LocalTime.of(18, 30), true)
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        service.message(new PatientPortalCareAiMessageRequest(tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")), "en"));
        service.message(new PatientPortalCareAiMessageRequest("evening", "en"));

        ArgumentCaptor<CareAiConversationTurnCommand> captor = ArgumentCaptor.forClass(CareAiConversationTurnCommand.class);
        verify(conversationPersistenceService, org.mockito.Mockito.atLeast(3)).safeRecordTurn(captor.capture());
        List<CareAiConversationTurnCommand> commands = captor.getAllValues();
        assertThat(commands.get(1).workflowSnapshot().contextJson()).contains("\"preferredDate\":\"" + tomorrow + "\"");
        assertThat(commands.get(2).workflowSnapshot().contextJson()).contains("\"preferredTimeWindow\":\"evening\"");
        assertThat(commands.get(2).workflowSnapshot().contextJson()).contains("\"timePreference\":true");
    }

    @Test
    void topicSwitchToClinicTimingDoesNotClearBookingWorkflow() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta tomorrow", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("what are clinic timings?", "en"));

        assertThat(response.assistantMessage()).contains("continue the current booking");
        assertThat(response.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(response.state().preferredDate()).isEqualTo(tomorrow.toString());
    }

    @Test
    void changeConversationDuringBookingAsksTopicSwitchClarification() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        service.message(new PatientPortalCareAiMessageRequest(tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")), "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("Can we switch the conversation?", "en"));

        assertThat(response.assistantMessage()).isEqualTo("Sure. Do you want to cancel this booking flow, or ask something else?");
        assertThat(response.assistantMessage()).doesNotContain("Please choose a time preference");
        assertThat(response.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
        verify(patientPortalService, never()).cancelAppointment(any(UUID.class), any(String.class));
    }

    @Test
    void switchTheConversationDuringBookingAsksTopicSwitchClarification() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        service.message(new PatientPortalCareAiMessageRequest(tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")), "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("switch the conversation", "en"));

        assertThat(response.assistantMessage()).isEqualTo("Sure. Do you want to cancel this booking flow, or ask something else?");
        assertThat(response.assistantMessage()).doesNotContain("Please choose a time preference");
        assertThat(response.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
    }

    @Test
    void ambiguousCancelAsksClarification() {
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("cancel that", "en"));

        assertThat(response.assistantMessage()).contains("stop the current booking flow");
        assertThat(response.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
    }

    @Test
    void staleConfirmationResetWhenSlotChanges() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(10, 0), true),
                slot(tomorrow, LocalTime.of(11, 0), true)
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta on " + tomorrow + " at 10 AM", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("Actually make it 11 AM", "en"));

        assertThat(response.assistantMessage()).contains("11:00");
        assertThat(response.state().suggestedSlot()).isEqualTo("11:00");
        assertThat(response.state().confirmationPending()).isTrue();

        ArgumentCaptor<CareAiConversationTurnCommand> captor = ArgumentCaptor.forClass(CareAiConversationTurnCommand.class);
        verify(conversationPersistenceService, org.mockito.Mockito.atLeast(2)).safeRecordTurn(captor.capture());
        assertThat(captor.getAllValues().getLast().workflowSnapshot().eventType()).isEqualTo("CONFIRMATION_RESET");
    }

    @Test
    void yesAfterStaleConfirmationDoesNotExecuteStaleAction() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(10, 0), true),
                slot(tomorrow, LocalTime.of(18, 0), true),
                slot(tomorrow, LocalTime.of(18, 30), true)
        ));

        service.message(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta on " + tomorrow + " at 10 AM", "en"));
        var resetResponse = service.message(new PatientPortalCareAiMessageRequest("Actually make it evening", "en"));
        var yesResponse = service.message(new PatientPortalCareAiMessageRequest("yes", "en"));

        verify(patientPortalService, never()).bookAppointment(any());
        assertThat(resetResponse.state().confirmationPending()).isFalse();
        assertThat(yesResponse.assistantMessage()).doesNotContain("Appointment booked successfully");
        assertThat(yesResponse.assistantMessage()).contains("Please choose");
    }

    @Test
    void voiceMessagePathPersistsVoiceChannelAndAppliesRepeatedQuestionGuard() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")));
        when(patientPortalService.doctorSlots("doctor-neha", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(18, 0), true),
                slot(tomorrow, LocalTime.of(18, 30), true)
        ));

        service.messageFromVoice(new PatientPortalCareAiMessageRequest("Book appointment with Dr Neha Mehta", "en"));
        service.messageFromVoice(new PatientPortalCareAiMessageRequest(tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu")), "en"));
        var response = service.messageFromVoice(new PatientPortalCareAiMessageRequest("Book in evening, three", "en"));

        ArgumentCaptor<CareAiConversationTurnCommand> captor = ArgumentCaptor.forClass(CareAiConversationTurnCommand.class);
        verify(conversationPersistenceService, org.mockito.Mockito.atLeast(3)).safeRecordTurn(captor.capture());
        assertThat(captor.getAllValues().getLast().channel().name()).isEqualTo("PATIENT_PORTAL_VOICE");
        assertThat(response.assistantMessage()).doesNotContain("What time works best");
        assertThat(response.assistantMessage()).doesNotContain("Please choose a time preference");
        assertThat(response.state().preferredTimeWindow()).isEqualTo("evening");
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

    private LocalDate nextDate(int month, int day) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate candidate = LocalDate.of(today.getYear(), month, day);
        return candidate.isBefore(today) ? candidate.plusYears(1) : candidate;
    }

    private CareAiReceptionistTaskEntity receptionistTask(String taskType) {
        return CareAiReceptionistTaskEntity.create(
                TENANT_A,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskType.valueOf(taskType),
                CareAiReceptionistTaskPriority.MEDIUM,
                "PATIENT_PORTAL_CHAT",
                "requested-receptionist",
                "help",
                null,
                null,
                java.time.OffsetDateTime.now().plusMinutes(15),
                "{}"
        );
    }

    private void setPatientContext(UUID tenantId, UUID appUserId) {
        setPatientContext(tenantId, appUserId, "corr-1");
    }

    private void setPatientContext(UUID tenantId, UUID appUserId, String correlationId) {
        RequestContextHolder.set(new RequestContext(new TenantId(tenantId), appUserId, "subject-1", Set.of("PATIENT"), "PATIENT", correlationId));
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
