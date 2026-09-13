package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.careai.CareAiTaskNotificationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskService;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiCanonicalSelectionRegressionTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID APP_USER_ID = UUID.randomUUID();
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    private PatientPortalService patientPortalService;
    private PatientPortalCareAiService service;

    @BeforeEach
    void setUp() {
        patientPortalService = mock(PatientPortalService.class);
        AiOrchestrationService aiOrchestrationService = mock(AiOrchestrationService.class);
        CareAiConversationPersistenceService conversationPersistenceService = mock(CareAiConversationPersistenceService.class);
        CareAiReceptionistTaskService receptionistTaskService = mock(CareAiReceptionistTaskService.class);
        CareAiTaskNotificationService taskNotificationService = mock(CareAiTaskNotificationService.class);
        ClinicTimeZoneResolver clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        VoiceTestProperties voiceTestProperties = new VoiceTestProperties();
        voiceTestProperties.getLlm().setMaxOutputTokens(1024);
        service = new PatientPortalCareAiService(
                patientPortalService,
                clinicTimeZoneResolver,
                new LlmBackedPatientPortalCareAiPlanner(aiOrchestrationService, new com.fasterxml.jackson.databind.ObjectMapper(), voiceTestProperties, true),
                conversationPersistenceService,
                receptionistTaskService,
                taskNotificationService
        );
        when(patientPortalService.currentPatientId()).thenReturn(UUID.randomUUID());
        when(clinicTimeZoneResolver.resolve(any())).thenReturn(CLINIC_ZONE);
        PatientPortalCareAiTestSupport.setPatientContext(TENANT_ID, APP_USER_ID);
    }

    @Test
    void multiTurnSelectionKeepsResolvedDoctorAndAdvancesBookingState() {
        LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
        when(patientPortalService.doctors()).thenReturn(List.of(
                PatientPortalCareAiTestSupport.doctor("doctor-akshu", "Dr Akshu Kumar", "General Medicine"),
                PatientPortalCareAiTestSupport.doctor("doctor-neha", "Dr Neha Mehta", "General Medicine"),
                PatientPortalCareAiTestSupport.doctor("doctor-ashish", "Dr Ashish Kumar", "General Medicine")
        ));
        when(patientPortalService.doctorSlots("doctor-ashish", tomorrow)).thenReturn(List.of(
                slot(tomorrow, LocalTime.of(9, 0), true),
                slot(tomorrow, LocalTime.of(10, 30), true),
                slot(tomorrow, LocalTime.of(11, 0), true)
        ));

        var first = service.message(new PatientPortalCareAiMessageRequest("I want to book an appointment.", "en"));
        assertThat(first.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");

        var second = service.message(new PatientPortalCareAiMessageRequest("General Medicine", "en"));
        assertThat(second.state().speciality()).isEqualTo("General Medicine");

        var third = service.message(new PatientPortalCareAiMessageRequest("second doctor", "en"));
        assertThat(third.state().doctorName()).isEqualTo("Dr Ashish Kumar");
        assertThat(third.state().speciality()).isEqualTo("General Medicine");
        assertThat(third.assistantMessage()).contains("What date would you prefer");

        var fourth = service.message(new PatientPortalCareAiMessageRequest("tomorrow morning", "en"));
        assertThat(fourth.state().doctorName()).isEqualTo("Dr Ashish Kumar");
        assertThat(fourth.state().speciality()).isEqualTo("General Medicine");
        assertThat(fourth.state().preferredDate()).isEqualTo(tomorrow.toString());
    }

    private PatientPortalDoctorSlotResponse slot(LocalDate date, LocalTime time, boolean selectable) {
        return new PatientPortalDoctorSlotResponse(
                null,
                date,
                time,
                null,
                "BOOKABLE",
                selectable
        );
    }
}
