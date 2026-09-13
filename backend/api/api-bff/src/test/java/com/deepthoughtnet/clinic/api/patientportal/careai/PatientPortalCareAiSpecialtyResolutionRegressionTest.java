package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.careai.CareAiTaskNotificationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskService;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiSpecialtyResolutionRegressionTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID APP_USER_ID = UUID.randomUUID();

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
        when(clinicTimeZoneResolver.resolve(any())).thenReturn(ZoneId.of("Asia/Kolkata"));
        PatientPortalCareAiTestSupport.setPatientContext(TENANT_ID, APP_USER_ID);
    }

    @Test
    void generalMedicineAdvancesBookingFlowToDatePrompt() {
        when(patientPortalService.doctors()).thenReturn(List.of(
                PatientPortalCareAiTestSupport.doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")
        ));

        service.message(new PatientPortalCareAiMessageRequest("I want to book an appointment.", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("General Medicine", "en"));

        assertThat(response.state().speciality()).isEqualTo("General Medicine");
        assertThat(response.assistantMessage()).contains("What date would you prefer");
    }

    @Test
    void generalPhysicianAdvancesBookingFlowToDatePrompt() {
        when(patientPortalService.doctors()).thenReturn(List.of(
                PatientPortalCareAiTestSupport.doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")
        ));

        service.message(new PatientPortalCareAiMessageRequest("I want to book an appointment.", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("General Physician", "en"));

        assertThat(response.state().speciality()).isEqualTo("General Medicine");
        assertThat(response.assistantMessage()).contains("What date would you prefer");
    }

    @Test
    void gpAdvancesBookingFlowToDatePrompt() {
        when(patientPortalService.doctors()).thenReturn(List.of(
                PatientPortalCareAiTestSupport.doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")
        ));

        service.message(new PatientPortalCareAiMessageRequest("I want to book an appointment.", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("GP", "en"));

        assertThat(response.state().speciality()).isEqualTo("General Medicine");
        assertThat(response.assistantMessage()).contains("What date would you prefer");
    }

    @Test
    void findDoctorGeneralPhysicianResolvesCanonicalSpecialty() {
        when(patientPortalService.doctors()).thenReturn(List.of(
                PatientPortalCareAiTestSupport.doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")
        ));

        var response = service.message(new PatientPortalCareAiMessageRequest("Find a general physician.", "en"));

        assertThat(response.assistantMessage()).contains("Dr Neha Mehta");
        assertThat(response.assistantMessage()).contains("General Medicine");
    }

    @Test
    void unsupportedSpecialtyTriggersSafeClarification() {
        when(patientPortalService.doctors()).thenReturn(List.of(
                PatientPortalCareAiTestSupport.doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")
        ));

        service.message(new PatientPortalCareAiMessageRequest("I want to book an appointment.", "en"));
        var response = service.message(new PatientPortalCareAiMessageRequest("quantum medicine", "en"));

        assertThat(response.assistantMessage()).contains("I couldn't match that specialty");
        assertThat(response.assistantMessage()).contains("General Medicine");
    }
}
