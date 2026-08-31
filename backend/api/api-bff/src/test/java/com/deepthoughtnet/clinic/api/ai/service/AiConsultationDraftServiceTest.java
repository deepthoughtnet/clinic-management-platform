package com.deepthoughtnet.clinic.api.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationNotesRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiPrescriptionTemplateRequest;
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

    @Test
    void suggestPrescriptionTemplateUsesOnlyCurrentConsultationContext() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        List<Map<String, Object>> capturedInputs = new java.util.ArrayList<>();
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = invocation.getArgument(3);
            capturedInputs.add(input);
            return new AiDraftResponse(
                    true,
                    false,
                    "AI draft generated.",
                    "MOCK",
                    "mock-model",
                    "{\"summary\":\"Review symptoms and prescribe only if indicated\",\"suggestions\":[]}",
                    Map.of(
                            "summary", "Review symptoms and prescribe only if indicated",
                            "suggestions", List.of()
                    ),
                    BigDecimal.valueOf(0.9),
                    List.of(),
                    List.of(),
                    null,
                    "VALID",
                    0,
                    "{\"summary\":\"Review symptoms and prescribe only if indicated\",\"suggestions\":[]}",
                    "VALID"
            );
        });

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        AiPrescriptionTemplateRequest request = new AiPrescriptionTemplateRequest(
                consultationId,
                patientId,
                "30Y / FEMALE",
                "BP 120/80, Pulse 88, Temp 38.1 C, SpO2 98%",
                null,
                "CBC pending",
                "Viral Upper Respiratory Infection",
                "Fever, cough, headache",
                null,
                null,
                "Observation and rest"
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr-prescription")
        );
        try {
            AiDraftResponse response = service.suggestPrescriptionTemplate(request);

            assertThat(response.parseStatus()).isEqualTo("VALID");
            verifyNoInteractions(clinicalContextService);
            assertThat(capturedInputs).hasSize(1);
            Map<String, Object> input = capturedInputs.get(0);
            assertThat(input).containsEntry("consultationId", consultationId);
            assertThat(input).containsEntry("patientId", patientId);
            assertThat(input).containsEntry("patientAgeGender", "30Y / FEMALE");
            assertThat(input).containsEntry("vitals", "BP 120/80, Pulse 88, Temp 38.1 C, SpO2 98%");
            assertThat(input).containsEntry("diagnosis", "Viral Upper Respiratory Infection");
            assertThat(input).containsEntry("symptoms", "Fever, cough, headache");
            assertThat(input).containsEntry("allergies", null);
            assertThat(input).containsEntry("currentMedications", null);
            assertThat(input).containsEntry("doctorNotes", "Observation and rest");
            assertThat(input).containsKey("prescriptionSuggestionContext");
            assertThat(input).doesNotContainKeys("clinicalContext", "clinicalContextSummary", "clinicalContextJson", "aiPromptContext");
            assertThat(input.get("prescriptionSuggestionContext").toString()).contains("Consultation diagnosis: Viral Upper Respiratory Infection");
            assertThat(input.get("prescriptionSuggestionContext").toString()).contains("Current symptoms: Fever, cough, headache");
            assertThat(input.get("prescriptionSuggestionContext").toString()).contains("Allergies: Not recorded");
            assertThat(input.get("prescriptionSuggestionContext").toString()).contains("Current medicines: Not recorded");
            assertThat(input.get("prescriptionSuggestionContext").toString()).doesNotContain("Penicillin");
            assertThat(input.get("prescriptionSuggestionContext").toString()).doesNotContain("Lisinopril");
            assertThat(input.get("prescriptionSuggestionContext").toString()).doesNotContain("previous visits");
            assertThat(input.get("prescriptionSuggestionContext").toString()).doesNotContain("longitudinal");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void suggestPrescriptionTemplateKeepsSequentialRequestsIsolated() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        List<Map<String, Object>> capturedInputs = new java.util.ArrayList<>();
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = invocation.getArgument(3);
            capturedInputs.add(input);
            return new AiDraftResponse(
                    true,
                    false,
                    "AI draft generated.",
                    "MOCK",
                    "mock-model",
                    "{\"summary\":\"Review context carefully\",\"suggestions\":[]}",
                    Map.of("summary", "Review context carefully", "suggestions", List.of()),
                    BigDecimal.valueOf(0.9),
                    List.of(),
                    List.of(),
                    null,
                    "VALID",
                    0,
                    "{\"summary\":\"Review context carefully\",\"suggestions\":[]}",
                    "VALID"
            );
        });

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr-seq")
        );
        try {
            AiDraftResponse first = service.suggestPrescriptionTemplate(new AiPrescriptionTemplateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "30Y / FEMALE",
                    "BP 118/76, Pulse 88",
                    null,
                    "CBC pending",
                    "Viral Upper Respiratory Infection",
                    "Fever, cough",
                    null,
                    "Paracetamol 500 mg as needed",
                    "Observation"
            ));
            AiDraftResponse second = service.suggestPrescriptionTemplate(new AiPrescriptionTemplateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "42Y / MALE",
                    "BP 130/82, Pulse 76",
                    "Ibuprofen 400 mg",
                    "X-ray pending",
                    "Mechanical Low Back Pain",
                    "Back pain",
                    "Penicillin",
                    "Lisinopril 10 mg",
                    "Reassessment"
            ));

            assertThat(first.parseStatus()).isEqualTo("VALID");
            assertThat(second.parseStatus()).isEqualTo("VALID");
            assertThat(capturedInputs).hasSize(2);
            assertThat(capturedInputs.get(0).get("prescriptionSuggestionContext").toString()).contains("Consultation diagnosis: Viral Upper Respiratory Infection");
            assertThat(capturedInputs.get(0).get("prescriptionSuggestionContext").toString()).doesNotContain("Mechanical Low Back Pain");
            assertThat(capturedInputs.get(1).get("prescriptionSuggestionContext").toString()).contains("Consultation diagnosis: Mechanical Low Back Pain");
            assertThat(capturedInputs.get(1).get("prescriptionSuggestionContext").toString()).doesNotContain("Viral Upper Respiratory Infection");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void suggestPrescriptionTemplateGroundsUnsupportedAssertionsAndPreservesExplicitContextAcrossSequentialRequests() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(ungroundedPrescriptionSuggestionResponse());

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        UUID tenantId = UUID.randomUUID();
        UUID consultationA = UUID.randomUUID();
        UUID patientA = UUID.randomUUID();
        UUID consultationB = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr-grounding")
        );
        try {
            AiDraftResponse first = service.suggestPrescriptionTemplate(new AiPrescriptionTemplateRequest(
                    consultationA,
                    patientA,
                    "30Y / FEMALE",
                    "BP 118/76, Pulse 88",
                    null,
                    "CBC pending",
                    "Viral Upper Respiratory Infection",
                    "Fever, cough, headache",
                    null,
                    null,
                    "Observation"
            ));
            AiDraftResponse second = service.suggestPrescriptionTemplate(new AiPrescriptionTemplateRequest(
                    consultationB,
                    patientB,
                    "42Y / MALE",
                    "BP 130/82, Pulse 76",
                    "Ibuprofen 400 mg",
                    "X-ray pending",
                    "Mechanical Low Back Pain",
                    "Back pain",
                    "Penicillin",
                    "Lisinopril 10 mg",
                    "Reassessment"
            ));
            AiDraftResponse third = service.suggestPrescriptionTemplate(new AiPrescriptionTemplateRequest(
                    consultationA,
                    patientA,
                    "30Y / FEMALE",
                    "BP 118/76, Pulse 88",
                    null,
                    "CBC pending",
                    "Viral Upper Respiratory Infection",
                    "Fever, cough, headache",
                    null,
                    null,
                    "Observation"
            ));

            assertThat(first.draft()).contains("No grounded prescription suggestions could be retained from the current consultation context.");
            assertThat(first.draft()).doesNotContain("Acute lower back pain");
            assertThat(first.draft()).doesNotContain("Penicillin allergy (rash)");
            assertThat(first.draft()).doesNotContain("Potential interaction with Lisinopril");
            assertThat(first.structuredData()).isNotNull();
            assertThat(first.structuredData().get("summary").toString()).contains("No grounded prescription suggestions could be retained from the current consultation context.");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> firstSuggestions = (List<Map<String, Object>>) first.structuredData().get("suggestions");
            assertThat(firstSuggestions).isEmpty();

            assertThat(second.draft()).contains("lower back pain");
            assertThat(second.draft()).contains("Potential interaction with Lisinopril");
            assertThat(second.structuredData().get("summary").toString()).contains("lower back pain");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> secondSuggestions = (List<Map<String, Object>>) second.structuredData().get("suggestions");
            assertThat(secondSuggestions).isNotEmpty();
            assertThat(secondSuggestions.get(0).get("reason").toString()).contains("lower back pain");
            assertThat(secondSuggestions.get(0).get("safetyNote").toString()).contains("Potential interaction with Lisinopril");

            assertThat(third.draft()).contains("No grounded prescription suggestions could be retained from the current consultation context.");
            assertThat(third.draft()).doesNotContain("Acute lower back pain");
            assertThat(third.draft()).doesNotContain("Penicillin allergy (rash)");
            assertThat(third.draft()).doesNotContain("Potential interaction with Lisinopril");
            assertThat(third.structuredData().get("summary").toString()).contains("No grounded prescription suggestions could be retained from the current consultation context.");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void suggestPrescriptionTemplateSuppressesUnsupportedDiseaseSpecificSuggestionsAndKeepsGroundedSymptomSuggestions() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(mixedDiseaseSpecificSuggestionResponse());

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr-mixed-disease")
        );
        try {
            AiDraftResponse response = service.suggestPrescriptionTemplate(new AiPrescriptionTemplateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "30Y / FEMALE",
                    "BP 118/76, Pulse 88",
                    null,
                    null,
                    "Viral Upper Respiratory Infection",
                    "Fever, cough, headache",
                    null,
                    null,
                    "Observation"
            ));

            assertThat(response.draft()).contains("Paracetamol 500 mg");
            assertThat(response.draft()).doesNotContain("Amoxicillin 500 mg");
            assertThat(response.draft()).doesNotContain("Suspected bacterial infection");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) response.structuredData().get("suggestions");
            assertThat(suggestions).hasSize(1);
            assertThat(suggestions.get(0).get("medicine").toString()).contains("Paracetamol 500 mg");
            assertThat(suggestions.get(0).get("reason").toString()).contains("Fever");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void suggestPrescriptionTemplateRetainsDiseaseSpecificSuggestionWhenCurrentConsultationEvidenceSupportsIt() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(amoxicillinSuggestionResponse());

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr-bacterial")
        );
        try {
            AiDraftResponse response = service.suggestPrescriptionTemplate(new AiPrescriptionTemplateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "30Y / FEMALE",
                    "BP 118/76, Pulse 88",
                    null,
                    null,
                    "Bacterial Sinusitis",
                    "Fever, cough, sinus pain",
                    null,
                    null,
                    "Observation"
            ));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) response.structuredData().get("suggestions");
            assertThat(suggestions).isNotEmpty();
            assertThat(suggestions.get(0).get("medicine").toString()).contains("Amoxicillin 500 mg");
            assertThat(suggestions.get(0).get("reason").toString()).contains("bacterial infection");
            assertThat(response.draft()).contains("Amoxicillin 500 mg");
            assertThat(response.draft()).doesNotContain("No grounded prescription suggestions could be retained from the current consultation context.");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void suggestPrescriptionTemplatePreservesExplicitPenicillinAndMedicationContext() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(ungroundedPrescriptionSuggestionResponse());

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr-grounding-explicit")
        );
        try {
            AiDraftResponse response = service.suggestPrescriptionTemplate(new AiPrescriptionTemplateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "42Y / MALE",
                    "BP 130/82, Pulse 76",
                    "Ibuprofen 400 mg",
                    "X-ray pending",
                    "Mechanical Low Back Pain",
                    "Back pain",
                    "Penicillin (rash)",
                    "Lisinopril 10 mg",
                    "Reassessment"
            ));

            assertThat(response.draft()).contains("lower back pain");
            assertThat(response.draft()).contains("Potential interaction with Lisinopril");
            assertThat(response.draft()).doesNotContain("Current consultation context does not support this specific indication");
            assertThat(response.structuredData().get("summary").toString()).contains("lower back pain");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = (List<Map<String, Object>>) response.structuredData().get("suggestions");
            assertThat(suggestions).isNotEmpty();
            assertThat(suggestions.get(0).get("reason").toString()).contains("lower back pain");
            assertThat(suggestions.get(0).get("safetyNote").toString()).contains("Potential interaction with Lisinopril");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void suggestPrescriptionTemplateGroundsUnsupportedCurrentMedicationAssertionsAndPreservesSupportedOnes() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(currentMedicationAssertionResponse("Ibuprofen"));

        AiConsultationDraftService service = new AiConsultationDraftService(copilotService, clinicalContextService);
        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr-current-med")
        );
        try {
            AiPrescriptionTemplateRequest unsupportedRequest = new AiPrescriptionTemplateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "42Y / MALE",
                    "BP 122/80, Pulse 78",
                    "Paracetamol 500 mg Tablet • Fluticasone Propionate Nasal Spray • Oxymetazoline Nasal Spray",
                    null,
                    "Viral Upper Respiratory Infection",
                    "Fever, cough, headache",
                    null,
                    "Paracetamol 500 mg Tablet • Fluticasone Propionate Nasal Spray • Oxymetazoline Nasal Spray",
                    "Supportive care"
            );
            AiDraftResponse unsupported = service.suggestPrescriptionTemplate(unsupportedRequest);

            assertThat(unsupported.draft()).contains("Current medication use is not recorded; if the patient is taking Ibuprofen, review interaction risk before prescribing.");
            assertThat(unsupported.draft()).contains("Acetaminophen 500 mg");
            assertThat(unsupported.draft()).doesNotContain("Patient is currently on Ibuprofen.");
            assertThat(unsupported.structuredData().get("summary").toString()).contains("Supportive care. Current medication use is not recorded; if the patient is taking Ibuprofen, review interaction risk before prescribing.");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> unsupportedSuggestions = (List<Map<String, Object>>) unsupported.structuredData().get("suggestions");
            assertThat(unsupportedSuggestions).isNotEmpty();

            when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(currentMedicationAssertionResponse("Ibuprofen and Metformin"));
            AiDraftResponse multipleUnsupported = service.suggestPrescriptionTemplate(unsupportedRequest);
            assertThat(multipleUnsupported.draft()).contains("Current medication use is not recorded; if the patient is taking Ibuprofen and Metformin, review interaction risk before prescribing.");
            assertThat(multipleUnsupported.draft()).doesNotContain("No grounded prescription suggestions could be retained from the current consultation context.");
            assertThat(multipleUnsupported.draft()).doesNotContain("Patient is currently on Ibuprofen and Metformin.");

            AiPrescriptionTemplateRequest supportedRequest = new AiPrescriptionTemplateRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "42Y / MALE",
                    "BP 122/80, Pulse 78",
                    "Ibuprofen 400 mg Tablet",
                    null,
                    "Viral Upper Respiratory Infection",
                    "Fever, cough, headache",
                    null,
                    "Ibuprofen 400 mg Tablet",
                    "Supportive care"
            );
            when(copilotService.draft(any(), anyString(), anyString(), anyMap(), any())).thenReturn(currentMedicationAssertionResponse("Ibuprofen"));
            AiDraftResponse supported = service.suggestPrescriptionTemplate(supportedRequest);

            assertThat(supported.draft()).contains("Patient is currently on Ibuprofen.");
            assertThat(supported.draft()).doesNotContain("Current medication use is not recorded; if the patient is taking Ibuprofen, review interaction risk before prescribing.");
            assertThat(supported.structuredData().get("summary").toString()).contains("Patient is currently on Ibuprofen.");
        } finally {
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

    private AiDraftResponse ungroundedPrescriptionSuggestionResponse() {
        Map<String, Object> suggestion = new java.util.LinkedHashMap<>();
        suggestion.put("itemId", "item-1");
        suggestion.put("medicine", "Ibuprofen 400 mg");
        suggestion.put("dose", "400 mg");
        suggestion.put("frequency", "TID");
        suggestion.put("duration", "5 days");
        suggestion.put("reason", "Acute lower back pain");
        suggestion.put("safetyNote", "Potential interaction with Lisinopril");
        suggestion.put("draftText", "Medicine: Ibuprofen 400 mg\nReason: Acute lower back pain\nSafety: Potential interaction with Lisinopril");
        suggestion.put("status", "PENDING");

        Map<String, Object> structuredData = new java.util.LinkedHashMap<>();
        structuredData.put("summary", "Recommend treatment for acute lower back pain");
        structuredData.put("suggestions", List.of(suggestion));
        return new AiDraftResponse(
                true,
                false,
                "AI draft generated.",
                "MOCK",
                "mock-model",
                "Medicine: Ibuprofen 400 mg\nReason: Acute lower back pain\nSafety: Potential interaction with Lisinopril\nPenicillin allergy (rash)",
                structuredData,
                BigDecimal.valueOf(0.9),
                List.of("Review before prescribing"),
                List.of("Potential interaction with Lisinopril"),
                null,
                "VALID",
                0,
                "Medicine: Ibuprofen 400 mg\nReason: Acute lower back pain\nSafety: Potential interaction with Lisinopril\nPenicillin allergy (rash)",
                "VALID"
        );
    }

    private AiDraftResponse amoxicillinSuggestionResponse() {
        Map<String, Object> suggestion = new java.util.LinkedHashMap<>();
        suggestion.put("itemId", "item-amoxicillin");
        suggestion.put("medicine", "Amoxicillin 500 mg");
        suggestion.put("dose", "500 mg");
        suggestion.put("frequency", "TID");
        suggestion.put("duration", "5 days");
        suggestion.put("reason", "Suspected bacterial infection");
        suggestion.put("safetyNote", "Review before prescribing.");
        suggestion.put("draftText", "Medicine: Amoxicillin 500 mg\nReason: Suspected bacterial infection\nSafety: Review before prescribing.");
        suggestion.put("status", "PENDING");

        Map<String, Object> structuredData = new java.util.LinkedHashMap<>();
        structuredData.put("summary", "Suspected bacterial infection.");
        structuredData.put("suggestions", List.of(suggestion));
        return new AiDraftResponse(
                true,
                false,
                "AI draft generated.",
                "MOCK",
                "mock-model",
                "Medicine: Amoxicillin 500 mg\nReason: Suspected bacterial infection\nSafety: Review before prescribing.",
                structuredData,
                BigDecimal.valueOf(0.9),
                List.of("Review before prescribing"),
                List.of("Review before prescribing."),
                null,
                "VALID",
                0,
                "Medicine: Amoxicillin 500 mg\nReason: Suspected bacterial infection\nSafety: Review before prescribing.",
                "VALID"
        );
    }

    private AiDraftResponse mixedDiseaseSpecificSuggestionResponse() {
        Map<String, Object> feverSuggestion = new java.util.LinkedHashMap<>();
        feverSuggestion.put("itemId", "item-paracetamol");
        feverSuggestion.put("medicine", "Paracetamol 500 mg");
        feverSuggestion.put("dose", "500 mg");
        feverSuggestion.put("frequency", "TID");
        feverSuggestion.put("duration", "3 days");
        feverSuggestion.put("reason", "Fever");
        feverSuggestion.put("safetyNote", "Review before prescribing.");
        feverSuggestion.put("draftText", "Medicine: Paracetamol 500 mg\nReason: Fever\nSafety: Review before prescribing.");
        feverSuggestion.put("status", "PENDING");

        Map<String, Object> amoxicillinSuggestion = new java.util.LinkedHashMap<>();
        amoxicillinSuggestion.put("itemId", "item-amoxicillin");
        amoxicillinSuggestion.put("medicine", "Amoxicillin 500 mg");
        amoxicillinSuggestion.put("dose", "500 mg");
        amoxicillinSuggestion.put("frequency", "TID");
        amoxicillinSuggestion.put("duration", "5 days");
        amoxicillinSuggestion.put("reason", "Suspected bacterial infection");
        amoxicillinSuggestion.put("safetyNote", "Review before prescribing.");
        amoxicillinSuggestion.put("draftText", "Medicine: Amoxicillin 500 mg\nReason: Suspected bacterial infection\nSafety: Review before prescribing.");
        amoxicillinSuggestion.put("status", "PENDING");

        Map<String, Object> structuredData = new java.util.LinkedHashMap<>();
        structuredData.put("summary", "Fever and possible bacterial infection.");
        structuredData.put("suggestions", List.of(feverSuggestion, amoxicillinSuggestion));
        return new AiDraftResponse(
                true,
                false,
                "AI draft generated.",
                "MOCK",
                "mock-model",
                "Medicine: Paracetamol 500 mg\nReason: Fever\nSafety: Review before prescribing.\nMedicine: Amoxicillin 500 mg\nReason: Suspected bacterial infection\nSafety: Review before prescribing.",
                structuredData,
                BigDecimal.valueOf(0.9),
                List.of("Review before prescribing"),
                List.of("Review before prescribing."),
                null,
                "VALID",
                0,
                "Medicine: Paracetamol 500 mg\nReason: Fever\nSafety: Review before prescribing.\nMedicine: Amoxicillin 500 mg\nReason: Suspected bacterial infection\nSafety: Review before prescribing.",
                "VALID"
        );
    }

    private AiDraftResponse currentMedicationAssertionResponse(String assertedMedicationText) {
        Map<String, Object> suggestion = new java.util.LinkedHashMap<>();
        suggestion.put("itemId", "item-current-med");
        suggestion.put("medicine", "Acetaminophen 500 mg");
        suggestion.put("dose", "500 mg");
        suggestion.put("frequency", "TID");
        suggestion.put("duration", "3 days");
        suggestion.put("reason", "Supportive care");
        suggestion.put("safetyNote", "Patient is currently on " + assertedMedicationText + ".");
        suggestion.put("draftText", "Medicine: Acetaminophen 500 mg\nReason: Supportive care\nSafety: Patient is currently on " + assertedMedicationText + ".");
        suggestion.put("status", "PENDING");

        Map<String, Object> structuredData = new java.util.LinkedHashMap<>();
        structuredData.put("summary", "Supportive care. Patient is currently on " + assertedMedicationText + ".");
        structuredData.put("suggestions", List.of(suggestion));
        return new AiDraftResponse(
                true,
                false,
                "AI draft generated.",
                "MOCK",
                "mock-model",
                "Medicine: Acetaminophen 500 mg\nReason: Supportive care\nSafety: Patient is currently on " + assertedMedicationText + ".",
                structuredData,
                BigDecimal.valueOf(0.9),
                List.of("Review before prescribing"),
                List.of("Patient is currently on " + assertedMedicationText + "."),
                null,
                "VALID",
                0,
                "Medicine: Acetaminophen 500 mg\nReason: Supportive care\nSafety: Patient is currently on " + assertedMedicationText + ".",
                "VALID"
        );
    }
}
