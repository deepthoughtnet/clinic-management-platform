package com.deepthoughtnet.clinic.api.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationNotesRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class AiConsultationDraftServiceTest {
    @AfterEach
    void tearDown() {
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
    }

    @Test
    void structureNotesUsesClinicalContextAndResolvedSoapFields() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(sampleContext());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = invocation.getArgument(0);
            input.put("clinicalContextSummary", "Sample summary");
            input.put("aiPromptContext", "Patient snapshot: Sample Patient");
            input.put("clinicalContextJson", "{\"patientSummary\":{\"patientName\":\"Sample Patient\"}}");
            return null;
        }).when(clinicalContextService).enrichPromptInput(anyMap(), any());
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "AI draft generated.",
                "MOCK",
                "mock-model",
                "{\"subjective\":\"Fever for 4 days\",\"objective\":\"Temp 38.1 C\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration and rest\"}",
                Map.of(
                        "subjective", "Fever for 4 days",
                        "objective", "Temp 38.1 C",
                        "assessment", "Viral syndrome",
                        "plan", "Hydration and rest"
                ),
                BigDecimal.valueOf(0.9),
                List.of(),
                List.of(),
                null,
                "VALID",
                0,
                "{\"subjective\":\"Fever for 4 days\",\"objective\":\"Temp 38.1 C\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration and rest\"}",
                "VALID"
        ));

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        AiConsultationNotesRequest request = new AiConsultationNotesRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "30Y / FEMALE",
                "Chief complaint text",
                "Penicillin",
                "Diabetes",
                "Paracetamol 500 mg",
                "CBC ordered",
                "Chief complaint text",
                "Fever, cough",
                "Viral syndrome",
                "Supportive care",
                "BP 120/80, Pulse 88, Temp 38.1 C, BMI 24.1",
                "Existing objective notes"
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr")
        );
        try {
            AiDraftResponse response = service.structureNotes(request);

            assertThat(response.draft()).contains("Fever for 4 days");
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
            verify(clinicalContextService).buildClinicalContext(tenantId, request.patientId(), request.consultationId());
            verify(copilotService).draft(
                    eq(AiTaskType.CONSULTATION_NOTE_STRUCTURING),
                    eq("clinic.consultation.structure-notes.v1"),
                    eq("consultation_structure_notes"),
                    inputCaptor.capture(),
                    eq(List.of())
            );
            Map<String, Object> input = inputCaptor.getValue();
            assertThat(input.get("chiefComplaint")).isEqualTo("Chief complaint text");
            assertThat(input.get("diagnosis")).isEqualTo("Viral syndrome");
            assertThat(input.get("advice")).isEqualTo("Supportive care");
            assertThat(input.get("vitals")).isEqualTo("BP 120/80, Pulse 88, Temp 38.1 C, BMI 24.1");
            assertThat(input.get("soapClinicalContext")).asString().contains("Patient profile:");
            assertThat(input.get("soapClinicalContext")).asString().contains("Current visit:");
            assertThat(input).containsKeys("clinicalContextSummary", "aiPromptContext", "clinicalContextJson");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void structureNotesRejectsAllPlaceholderSoapDraftsSafely() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(sampleContext());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = invocation.getArgument(0);
            input.put("clinicalContextSummary", "Sample summary");
            input.put("aiPromptContext", "Patient snapshot: Sample Patient");
            input.put("clinicalContextJson", "{\"patientSummary\":{\"patientName\":\"Sample Patient\"}}");
            return null;
        }).when(clinicalContextService).enrichPromptInput(anyMap(), any());
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "AI draft generated.",
                "MOCK",
                "mock-model",
                "{\"subjective\":\"-\",\"objective\":\"--\",\"assessment\":\"N/A\",\"plan\":\"Not available\"}",
                Map.of(
                        "subjective", "-",
                        "objective", "--",
                        "assessment", "N/A",
                        "plan", "Not available"
                ),
                BigDecimal.valueOf(0.9),
                List.of(),
                List.of(),
                null,
                "VALID",
                0,
                "{\"subjective\":\"-\",\"objective\":\"--\",\"assessment\":\"N/A\",\"plan\":\"Not available\"}",
                "VALID"
        ));

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        AiConsultationNotesRequest request = new AiConsultationNotesRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "30Y / FEMALE",
                "Chief complaint text",
                "Penicillin",
                "Diabetes",
                "Paracetamol 500 mg",
                "CBC ordered",
                "Chief complaint text",
                "Fever, cough",
                "Viral syndrome",
                "Supportive care",
                "BP 120/80, Pulse 88, Temp 38.1 C, BMI 24.1",
                "Existing objective notes"
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr")
        );
        try {
            AiDraftResponse response = service.structureNotes(request);

            assertThat(response.enabled()).isTrue();
            assertThat(response.parseStatus()).isEqualTo("FAILED");
            assertThat(response.draft()).contains("Unable to generate a meaningful SOAP draft");
            assertThat(response.structuredData()).isEmpty();
            assertThat(response.warnings()).contains("Unable to generate a meaningful SOAP draft from the available consultation context. Add or verify clinical details and retry.");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void structureNotesRetainsPartialMeaningfulSoapDraftsForReview() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(sampleContext());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = invocation.getArgument(0);
            input.put("clinicalContextSummary", "Sample summary");
            input.put("aiPromptContext", "Patient snapshot: Sample Patient");
            input.put("clinicalContextJson", "{\"patientSummary\":{\"patientName\":\"Sample Patient\"}}");
            return null;
        }).when(clinicalContextService).enrichPromptInput(anyMap(), any());
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "AI draft generated.",
                "MOCK",
                "mock-model",
                "{\"subjective\":\"Fever for 4 days\",\"objective\":\"BP 138/86, Pulse 96\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration and rest\"}",
                Map.of(
                        "subjective", "Fever for 4 days",
                        "plan", "Hydration and rest"
                ),
                BigDecimal.valueOf(0.9),
                List.of(),
                List.of(),
                null,
                "VALID",
                0,
                "{\"subjective\":\"Fever for 4 days\",\"objective\":\"BP 138/86, Pulse 96\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration and rest\"}",
                "VALID"
        ));

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        AiConsultationNotesRequest request = new AiConsultationNotesRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "30Y / FEMALE",
                "Chief complaint text",
                "Penicillin",
                "Diabetes",
                "Paracetamol 500 mg",
                "CBC ordered",
                "Chief complaint text",
                "Fever, cough",
                "Viral syndrome",
                "Supportive care",
                "BP 120/80, Pulse 88, Temp 38.1 C, BMI 24.1",
                "Existing objective notes"
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr")
        );
        try {
            AiDraftResponse response = service.structureNotes(request);

            assertThat(response.enabled()).isTrue();
            assertThat(response.parseStatus()).isEqualTo("VALID");
            assertThat(response.structuredData()).containsEntry("subjective", "Fever for 4 days");
            assertThat(response.structuredData()).containsEntry("plan", "Hydration and rest");
            assertThat(response.warnings()).doesNotContain("Unable to generate a meaningful SOAP draft from the available consultation context. Add or verify clinical details and retry.");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void structureNotesRejectsUnrecognizedSoapProseSafely() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(sampleContext());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = invocation.getArgument(0);
            input.put("clinicalContextSummary", "Sample summary");
            input.put("aiPromptContext", "Patient snapshot: Sample Patient");
            input.put("clinicalContextJson", "{\"patientSummary\":{\"patientName\":\"Sample Patient\"}}");
            return null;
        }).when(clinicalContextService).enrichPromptInput(anyMap(), any());
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "This is an AI-generated draft. Doctor must verify before use. Please review the patient carefully.",
                "MOCK",
                "mock-model",
                "This is an AI-generated draft. Doctor must verify before use. Please review the patient carefully.",
                Map.of("raw", "This is an AI-generated draft. Doctor must verify before use. Please review the patient carefully."),
                BigDecimal.valueOf(0.9),
                List.of(),
                List.of(),
                null,
                "VALID",
                0,
                "This is an AI-generated draft. Doctor must verify before use. Please review the patient carefully.",
                "VALID"
        ));

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        AiConsultationNotesRequest request = new AiConsultationNotesRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "30Y / FEMALE",
                "Chief complaint text",
                "Penicillin",
                "Diabetes",
                "Paracetamol 500 mg",
                "CBC ordered",
                "Chief complaint text",
                "Fever, cough",
                "Viral syndrome",
                "Supportive care",
                "BP 120/80, Pulse 88, Temp 38.1 C, BMI 24.1",
                "Existing objective notes"
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr")
        );
        try {
            AiDraftResponse response = service.structureNotes(request);

            assertThat(response.enabled()).isTrue();
            assertThat(response.parseStatus()).isEqualTo("FAILED");
            assertThat(response.structuredData()).isEmpty();
            assertThat(response.warnings()).contains("Unable to generate a meaningful SOAP draft from the available consultation context. Add or verify clinical details and retry.");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void structureNotesEmitsSoapTraceStagesAndTraceIdWhenEnabled() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(sampleContext());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = invocation.getArgument(0);
            input.put("clinicalContextSummary", "Sample summary");
            input.put("aiPromptContext", "Patient snapshot: Sample Patient");
            input.put("clinicalContextJson", "{\"patientSummary\":{\"patientName\":\"Sample Patient\"}}");
            return null;
        }).when(clinicalContextService).enrichPromptInput(anyMap(), any());
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "AI draft generated.",
                "MOCK",
                "mock-model",
                "{\"subjective\":\"Fever for 4 days\",\"objective\":\"Temp 38.1 C\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration and rest\"}",
                Map.of(
                        "subjective", "Fever for 4 days",
                        "objective", "Temp 38.1 C",
                        "assessment", "Viral syndrome",
                        "plan", "Hydration and rest"
                ),
                BigDecimal.valueOf(0.9),
                List.of(),
                List.of(),
                null,
                "VALID",
                0,
                "{\"subjective\":\"Fever for 4 days\",\"objective\":\"Temp 38.1 C\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration and rest\"}",
                "VALID"
        ));

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        service.setSoapTraceEnabled(true);
        AiConsultationNotesRequest request = new AiConsultationNotesRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "30Y / FEMALE",
                "Chief complaint text",
                "Penicillin",
                "Diabetes",
                "Paracetamol 500 mg",
                "CBC ordered",
                "Chief complaint text",
                "Fever, cough",
                "Viral syndrome",
                "Supportive care",
                "BP 120/80, Pulse 88, Temp 38.1 C, BMI 24.1",
                "Existing objective notes"
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr-trace")
        );
        Logger logger = (Logger) LoggerFactory.getLogger(AiConsultationDraftService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AiDraftResponse response = service.structureNotes(request);

            assertThat(response.draft()).contains("Fever for 4 days");
            String messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
            assertThat(messages).contains("SOAP-DRAFT-TRACE stage=START");
            assertThat(messages).contains("SOAP-DRAFT-TRACE stage=CONTEXT_ENRICHED");
            assertThat(messages).contains("SOAP-DRAFT-TRACE stage=VALIDATION");
            assertThat(messages).contains("SOAP-DRAFT-TRACE stage=SERVICE_RESPONSE");
            assertThat(messages).contains("traceId=corr-trace");
            assertThat(messages).contains("exactReasonCode=SOAP_VALID");
        } finally {
            logger.detachAppender(appender);
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    private ClinicalContextResponse sampleContext() {
        return new ClinicalContextResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ClinicalContextResponse.PatientSnapshot("Sample Patient", 30, "FEMALE", "Diabetes", "Penicillin", List.of("Metformin"), "2026-07-01"),
                List.of(),
                new ClinicalContextResponse.MedicationSummary(List.of("Metformin"), List.of(), List.of(), List.of(), List.of("Allergy warning")),
                new ClinicalContextResponse.DiagnosisSummary("Viral fever", List.of("Gastritis")),
                new ClinicalContextResponse.IntakeSummary(
                        true,
                        "Fever and cough",
                        null,
                        "170 cm / 72 kg",
                        List.of("Pulse elevated"),
                        "Referral Letter uploaded",
                        "Needs quick review",
                        "Reception Desk",
                        "2026-07-04T09:00:00Z"
                ),
                new ClinicalContextResponse.LabIntelligence("2026-07-01 - CBC", List.of(), List.of(), List.of(), null, null, null, null, null, null, null),
                new ClinicalContextResponse.DocumentIntelligence(List.of(), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.TimelineSummary(List.of(), "2026-07-01 - Consultation"),
                new ClinicalContextResponse.LongitudinalMemory(List.of(), List.of(), null, null, List.of(), null, null, List.of(), List.of(), null),
                null,
                "Sample summary",
                "Patient snapshot: Sample Patient",
                "{\"patientSummary\":{\"patientName\":\"Sample Patient\"}}",
                OffsetDateTime.now()
        );
    }
}
