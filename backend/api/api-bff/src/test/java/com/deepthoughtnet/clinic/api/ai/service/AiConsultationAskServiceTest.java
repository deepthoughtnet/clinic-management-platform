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
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationAskRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
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
    void askUsesGenericCopilotAndForwardsPromptContext() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalContextService clinicalContextService = mock(ClinicalContextService.class);
        org.mockito.Mockito.doCallRealMethod().when(clinicalContextService).enrichPromptInput(anyMap(), any(ClinicalContextResponse.class));
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
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
            verify(copilotService).draft(
                    eq(AiTaskType.GENERIC_COPILOT),
                    eq("generic.copilot.v1"),
                    eq("consultation.ask"),
                    inputCaptor.capture(),
                    eq(List.of())
            );
            Map<String, Object> input = inputCaptor.getValue();
            assertThat(input.get("prompt")).isEqualTo("What should I watch for?");
            assertThat(input.get("patientAgeGender")).isEqualTo("30Y / FEMALE");
            assertThat(input.get("currentPrescriptionDraft")).isEqualTo("Paracetamol 500 mg");
            assertThat(input.get("chiefComplaints")).isEqualTo("Fever");
            assertThat(input.get("diagnosis")).isEqualTo("Viral syndrome");
            assertThat(input).containsKeys("clinicalContextSummary", "clinicalContextJson", "aiPromptContext");
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
                "Sample summary",
                "Patient snapshot: Sample Patient",
                "{\"patientSummary\":{\"patientName\":\"Sample Patient\"}}",
                java.time.OffsetDateTime.now()
        );
    }
}
