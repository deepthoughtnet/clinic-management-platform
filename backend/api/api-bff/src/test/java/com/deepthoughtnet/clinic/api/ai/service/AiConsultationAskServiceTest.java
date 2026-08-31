package com.deepthoughtnet.clinic.api.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationAskRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiConsultationAskServiceTest {
    @Test
    void askUsesCompactConsultationPromptContext() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(sampleContext());
        when(copilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "Answer",
                "MOCK",
                "mock-model",
                "{\"answer\":\"Check hydration and fever\"}",
                Map.of("answer", "Check hydration and fever"),
                BigDecimal.valueOf(0.9),
                List.of("Review"),
                List.of("Advisory"),
                null
        ));

        AiConsultationAskService service = new AiConsultationAskService(copilotService, clinicalContextService);
        AiConsultationAskRequest request = new AiConsultationAskRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "What should I watch for?",
                "30Y / FEMALE",
                "Vitals stable",
                "None",
                "Hypertension",
                "Paracetamol 500 mg",
                "CBC ordered",
                "Fever",
                "Cough",
                "Vitals stable",
                "Viral syndrome",
                "Supportive care"
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr")
        );
        try {
            AiDraftResponse response = service.ask(request);

            assertThat(response.draft()).contains("Check hydration");
            verify(clinicalContextService).buildClinicalContext(eq(tenantId), eq(request.patientId()), eq(request.consultationId()));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
            verify(copilotService).draft(
                    eq(AiTaskType.GENERIC_COPILOT),
                    eq("clinic.consultation.ask.v1"),
                    eq("consultation.ask"),
                    inputCaptor.capture(),
                    eq(List.of())
            );
            Map<String, Object> input = inputCaptor.getValue();
            assertThat(input.get("prompt")).isEqualTo("What should I watch for?");
            assertThat(input.get("aiPromptContext")).isEqualTo("Patient snapshot: Sample Patient");
            assertThat(input).containsKeys("clinicalContextSummary", "aiPromptContext");
            assertThat(input).doesNotContainKey("clinicalContextJson");
            assertThat(input).doesNotContainKey("clinicalContext");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void explainDiagnosisShortcutUsesGroundedDiagnosisContextAndDedicatedTemplate() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(sampleContext());
        when(copilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "Answer",
                "MOCK",
                "mock-model",
                "{\"answer\":\"Viral syndrome is supported by fever and cough\"}",
                Map.of("answer", "Viral syndrome is supported by fever and cough"),
                BigDecimal.valueOf(0.9),
                List.of("Review"),
                List.of("Advisory"),
                null
        ));

        AiConsultationAskService service = new AiConsultationAskService(copilotService, clinicalContextService);
        AiConsultationAskRequest request = new AiConsultationAskRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Explain diagnosis",
                "30Y / FEMALE",
                "Vitals stable",
                "None",
                "Hypertension",
                "Paracetamol 500 mg",
                "CBC ordered",
                "Fever",
                "Cough",
                "Vitals stable",
                "Viral syndrome",
                "Supportive care"
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr")
        );
        try {
            AiDraftResponse response = service.ask(request);

            assertThat(response.draft()).contains("Viral syndrome is supported");
            verify(clinicalContextService).buildClinicalContext(eq(tenantId), eq(request.patientId()), eq(request.consultationId()));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
            verify(copilotService).draft(
                    eq(AiTaskType.GENERIC_COPILOT),
                    eq("clinic.consultation.explain-diagnosis.v1"),
                    eq("consultation.explain-diagnosis"),
                    inputCaptor.capture(),
                    eq(List.of())
            );
            Map<String, Object> input = inputCaptor.getValue();
            assertThat(input.get("prompt")).isEqualTo("Explain diagnosis");
            assertThat(String.valueOf(input.get("diagnosisExplanationContext"))).contains("Working diagnosis: Viral syndrome");
            assertThat(String.valueOf(input.get("diagnosisExplanationContext"))).contains("Chief complaints: Fever");
            assertThat(String.valueOf(input.get("diagnosisExplanationContext"))).contains("Pending investigations: CBC ordered");
            assertThat(input).containsKey("diagnosisExplanationContext");
            assertThat(input).doesNotContainKey("aiPromptContext");
            assertThat(input).doesNotContainKey("clinicalContextSummary");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void explainDiagnosisShortcutMarksMissingDiagnosisExplicitly() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(sampleContext());
        when(copilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "Answer",
                "MOCK",
                "mock-model",
                "{\"answer\":\"No diagnosis recorded\"}",
                Map.of("answer", "No diagnosis recorded"),
                BigDecimal.valueOf(0.9),
                List.of("Review"),
                List.of("Advisory"),
                null
        ));

        AiConsultationAskService service = new AiConsultationAskService(copilotService, clinicalContextService);
        AiConsultationAskRequest request = new AiConsultationAskRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Explain diagnosis",
                "30Y / FEMALE",
                "Vitals stable",
                "None",
                "Hypertension",
                "Paracetamol 500 mg",
                "CBC ordered",
                "Fever",
                "Cough",
                "Vitals stable",
                "",
                "Supportive care"
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr")
        );
        try {
            service.ask(request);

            verify(clinicalContextService).buildClinicalContext(eq(tenantId), eq(request.patientId()), eq(request.consultationId()));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
            verify(copilotService).draft(
                    eq(AiTaskType.GENERIC_COPILOT),
                    eq("clinic.consultation.explain-diagnosis.v1"),
                    eq("consultation.explain-diagnosis"),
                    inputCaptor.capture(),
                    eq(List.of())
            );
            Map<String, Object> input = inputCaptor.getValue();
            assertThat(String.valueOf(input.get("diagnosisExplanationContext"))).contains("Working diagnosis: Not recorded");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void historyGapShortcutUsesNarrowClinicalGapContextAndSeparateClinicianReview() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(sampleContext());
        when(copilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "Answer",
                "MOCK",
                "mock-model",
                "Ask about symptom duration and allergies",
                Map.of("answer", "Ask about symptom duration and allergies"),
                BigDecimal.valueOf(0.9),
                List.of("Review"),
                List.of("Advisory"),
                null
        ));

        AiConsultationAskService service = new AiConsultationAskService(copilotService, clinicalContextService);
        AiConsultationAskRequest request = new AiConsultationAskRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "What else should I ask?",
                "30Y / FEMALE",
                null,
                "",
                "",
                null,
                null,
                "Fever",
                "",
                null,
                null,
                null
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr")
        );
        try {
            AiDraftResponse response = service.ask(request);

            assertThat(response.draft()).contains("Ask about symptom duration");
            verify(clinicalContextService).buildClinicalContext(eq(tenantId), eq(request.patientId()), eq(request.consultationId()));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
            verify(copilotService).draft(
                    eq(AiTaskType.GENERIC_COPILOT),
                    eq("clinic.consultation.history-gaps.v1"),
                    eq("consultation.history-gaps"),
                    inputCaptor.capture(),
                    eq(List.of())
            );
            Map<String, Object> input = inputCaptor.getValue();
            assertThat(input.get("prompt")).isEqualTo("What else should I ask?");
            assertThat(String.valueOf(input.get("historyGapContext"))).contains("Working diagnosis: Not recorded");
            assertThat(String.valueOf(input.get("historyGapContext"))).contains("Questions to ask the patient");
            assertThat(String.valueOf(input.get("historyGapContext"))).contains("Ask about symptom duration, progression, severity, and associated symptoms");
            assertThat(String.valueOf(input.get("historyGapContext"))).contains("Ask about associated symptoms, including respiratory red flags");
            assertThat(String.valueOf(input.get("historyGapContext"))).doesNotContain("Reason: Pati");
            assertThat(String.valueOf(input.get("historyGapContext"))).doesNotContain("clinical notes");
            assertThat(String.valueOf(input.get("clinicianReviewContext"))).contains("Latest lab report");
            assertThat(String.valueOf(input.get("clinicianReviewContext"))).contains("CBC");
            assertThat(input).doesNotContainKey("aiPromptContext");
            assertThat(input).doesNotContainKey("clinicalContextSummary");
            assertThat(input).doesNotContainKey("diagnosisExplanationContext");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void suggestTestsShortcutUsesGroundedInvestigationContextAndAvoidsDuplicateCBC() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(sampleSuggestTestsContextWithPendingCBC());
        when(copilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "Answer",
                "MOCK",
                "mock-model",
                "Consider CBC only if a new clinical reason exists",
                Map.of("answer", "Consider CBC only if a new clinical reason exists"),
                BigDecimal.valueOf(0.9),
                List.of("Review"),
                List.of("Advisory"),
                null
        ));

        AiConsultationAskService service = new AiConsultationAskService(copilotService, clinicalContextService);
        AiConsultationAskRequest request = new AiConsultationAskRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Suggest tests",
                "30Y / FEMALE",
                "Temp 38.7 C, SpO2 92",
                "None",
                "Hypertension",
                null,
                "CBC pending",
                "Fever and cough",
                "Shortness of breath",
                "Concern for pneumonia",
                "Viral syndrome",
                "Supportive care"
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr")
        );
        try {
            AiDraftResponse response = service.ask(request);

            assertThat(response.draft()).contains("CBC only if a new clinical reason exists");
            verify(clinicalContextService).buildClinicalContext(eq(tenantId), eq(request.patientId()), eq(request.consultationId()));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
            verify(copilotService).draft(
                    eq(AiTaskType.GENERIC_COPILOT),
                    eq("clinic.consultation.suggest-tests.v1"),
                    eq("consultation.suggest-tests"),
                    inputCaptor.capture(),
                    eq(List.of())
            );
            Map<String, Object> input = inputCaptor.getValue();
            String investigationSuggestionContext = String.valueOf(input.get("investigationSuggestionContext"));
            assertThat(investigationSuggestionContext).contains("Working diagnosis: Viral syndrome");
            assertThat(investigationSuggestionContext).contains("Current consultation lab orders: CBC pending");
            assertThat(investigationSuggestionContext).contains("Pending investigations: CBC");
            assertThat(investigationSuggestionContext).contains("Available investigation evidence");
            assertThat(investigationSuggestionContext).contains("Latest lab report");
            assertThat(investigationSuggestionContext).contains("Do not automatically create, select, or map a catalog test");
            assertThat(investigationSuggestionContext).contains("Do not include billing, payment, internal AI metadata");
            assertThat(investigationSuggestionContext).contains("Consider if indicated");
            assertThat(investigationSuggestionContext).contains("Not currently necessary / insufficient information");
            assertThat(input).doesNotContainKey("aiPromptContext");
            assertThat(input).doesNotContainKey("clinicalContextSummary");
            assertThat(input).doesNotContainKey("diagnosisExplanationContext");
            assertThat(input).doesNotContainKey("historyGapContext");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    @Test
    void suggestTestsShortcutHandlesInsufficientInformationAndNoExistingInvestigations() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        when(clinicalContextService.buildClinicalContext(any(UUID.class), any(UUID.class), any(UUID.class))).thenReturn(sampleSuggestTestsContextWithoutInvestigations());
        when(copilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "Answer",
                "MOCK",
                "mock-model",
                "Insufficient information to recommend a specific test",
                Map.of("answer", "Insufficient information to recommend a specific test"),
                BigDecimal.valueOf(0.9),
                List.of("Review"),
                List.of("Advisory"),
                null
        ));

        AiConsultationAskService service = new AiConsultationAskService(copilotService, clinicalContextService);
        AiConsultationAskRequest request = new AiConsultationAskRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Suggest tests",
                "38Y / MALE",
                null,
                "",
                "",
                null,
                null,
                "",
                "",
                "",
                "",
                null
        );

        UUID tenantId = UUID.randomUUID();
        com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.set(
                new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of(), "DOCTOR", "corr")
        );
        try {
            AiDraftResponse response = service.ask(request);

            assertThat(response.draft()).contains("Insufficient information");
            verify(clinicalContextService).buildClinicalContext(eq(tenantId), eq(request.patientId()), eq(request.consultationId()));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
            verify(copilotService).draft(
                    eq(AiTaskType.GENERIC_COPILOT),
                    eq("clinic.consultation.suggest-tests.v1"),
                    eq("consultation.suggest-tests"),
                    inputCaptor.capture(),
                    eq(List.of())
            );
            Map<String, Object> input = inputCaptor.getValue();
            String investigationSuggestionContext = String.valueOf(input.get("investigationSuggestionContext"));
            assertThat(investigationSuggestionContext).contains("Working diagnosis: Not recorded");
            assertThat(investigationSuggestionContext).contains("Already ordered/pending investigations: None documented.");
            assertThat(investigationSuggestionContext).contains("Available investigation evidence: No recent investigation result is documented.");
            assertThat(investigationSuggestionContext).contains("Do not include billing, payment, internal AI metadata");
            assertThat(input).doesNotContainKey("aiPromptContext");
            assertThat(input).doesNotContainKey("clinicalContextSummary");
            assertThat(input).doesNotContainKey("diagnosisExplanationContext");
            assertThat(input).doesNotContainKey("historyGapContext");
        } finally {
            com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.clear();
        }
    }

    private ClinicalContextResponse sampleContext() {
        return new ClinicalContextResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ClinicalContextResponse.PatientSnapshot("Sample Patient", 30, "FEMALE", "Hypertension", "Penicillin", List.of("Metformin"), "2026-07-01"),
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
                java.time.OffsetDateTime.now()
        );
    }

    private ClinicalContextResponse sampleSuggestTestsContextWithPendingCBC() {
        return new ClinicalContextResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ClinicalContextResponse.PatientSnapshot("Sample Patient", 30, "FEMALE", "Hypertension", "Penicillin", List.of("Metformin"), "2026-07-01"),
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
                new ClinicalContextResponse.LabIntelligence(
                        "2026-07-01 - CBC",
                        List.of("Hemoglobin low"),
                        List.of("CBC stable"),
                        List.of("CBC"),
                        "6.8%",
                        "CBC",
                        "Creatinine normal",
                        "Random blood sugar 126 mg/dL",
                        "Lipid profile acceptable",
                        "BP 138/86",
                        "24.2"
                ),
                new ClinicalContextResponse.DocumentIntelligence(List.of(), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.TimelineSummary(List.of(), "2026-07-01 - Consultation"),
                new ClinicalContextResponse.LongitudinalMemory(List.of(), List.of(), null, null, List.of(), null, null, List.of(), List.of(), null),
                null,
                "Sample summary",
                "Patient snapshot: Sample Patient",
                "{\"patientSummary\":{\"patientName\":\"Sample Patient\"}}",
                java.time.OffsetDateTime.now()
        );
    }

    private ClinicalContextResponse sampleSuggestTestsContextWithoutInvestigations() {
        return new ClinicalContextResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ClinicalContextResponse.PatientSnapshot("Sample Patient", 38, "MALE", null, null, List.of(), null),
                List.of(),
                new ClinicalContextResponse.MedicationSummary(List.of(), List.of(), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.DiagnosisSummary(null, List.of()),
                new ClinicalContextResponse.IntakeSummary(
                        true,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        null
                ),
                new ClinicalContextResponse.LabIntelligence(null, List.of(), List.of(), List.of(), null, null, null, null, null, null, null),
                new ClinicalContextResponse.DocumentIntelligence(List.of(), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.TimelineSummary(List.of(), null),
                new ClinicalContextResponse.LongitudinalMemory(List.of(), List.of(), null, null, List.of(), null, null, List.of(), List.of(), null),
                null,
                "Sample summary",
                "Patient snapshot: Sample Patient",
                "{\"patientSummary\":{\"patientName\":\"Sample Patient\"}}",
                java.time.OffsetDateTime.now()
        );
    }
}
