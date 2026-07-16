package com.deepthoughtnet.clinic.api.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;

class AiDoctorCopilotServiceTest {
    @Test
    void consultationAskUsesBoundedFreeformChatConfiguration() {
        AiOrchestrationService orchestrationService = mock(AiOrchestrationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<AiOrchestrationRequest> captured = new AtomicReference<>();
        when(orchestrationService.complete(any(AiOrchestrationRequest.class))).thenAnswer(invocation -> {
            AiOrchestrationRequest request = invocation.getArgument(0);
            captured.set(request);
            return new AiOrchestrationResponse(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    AiProductCode.CLINIC,
                    request.taskType(),
                    "GEMINI",
                    "gemini-2.5-flash",
                    "Concise answer with trends.",
                    "{\"answer\":\"Concise answer with trends.\"}",
                    BigDecimal.valueOf(0.92),
                    List.of(),
                    List.of("Review clinically"),
                    List.of("Advisory only"),
                    new AiTokenUsage(780L, 140L, 920L, BigDecimal.valueOf(0.02)),
                    321L,
                    false,
                    null,
                    "STOP",
                    "COMPLETE",
                    28,
                    "Concise answer with trends.",
                    "VALID"
            );
        });

        AiDoctorCopilotService service = new AiDoctorCopilotService(orchestrationService, objectMapper, true);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", java.util.Set.of(), "DOCTOR", "corr"));
        try {
            AiDraftResponse response = service.draft(
                    AiTaskType.GENERIC_COPILOT,
                    "clinic.consultation.ask.v1",
                    "consultation.ask",
                    Map.of("prompt", "Summarize the history", "aiPromptContext", "Patient snapshot"),
                    List.of()
            );

            assertThat(response.draft()).contains("Concise answer");
            assertThat(captured.get()).isNotNull();
            assertThat(captured.get().taskType()).isEqualTo(AiTaskType.GENERIC_COPILOT);
            assertThat(captured.get().promptTemplateCode()).isEqualTo("clinic.consultation.ask.v1");
            assertThat(captured.get().useCaseCode()).isEqualTo("consultation.ask");
            assertThat(captured.get().maxTokens()).isEqualTo(1024);
            assertThat(captured.get().temperature()).isEqualTo(0.1d);
        } finally {
            RequestContextHolder.clear();
        }
    }

    @Test
    void consultationSoapUsesStrictJsonAndLowercaseStructuredKeys() {
        AiOrchestrationService orchestrationService = mock(AiOrchestrationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<AiOrchestrationRequest> captured = new AtomicReference<>();
        when(orchestrationService.complete(any(AiOrchestrationRequest.class))).thenAnswer(invocation -> {
            AiOrchestrationRequest request = invocation.getArgument(0);
            captured.set(request);
            return orchestrationResponse(
                    "{\"subjective\":\"Fever and cough\",\"objective\":\"BP 138/86, Pulse 96\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration\"}",
                    "{\"subjective\":\"Fever and cough\",\"objective\":\"BP 138/86, Pulse 96\",\"assessment\":\"Viral syndrome\",\"plan\":\"Hydration\"}"
            );
        });

        AiDoctorCopilotService service = new AiDoctorCopilotService(orchestrationService, objectMapper, true);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", java.util.Set.of(), "DOCTOR", "corr"));
        try {
            AiDraftResponse response = service.draft(
                    AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                    "clinic.consultation.structure-notes.v1",
                    "consultation_structure_notes",
                    Map.of("consultationId", UUID.randomUUID(), "patientId", UUID.randomUUID()),
                    List.of()
            );

            assertThat(captured.get()).isNotNull();
            assertThat(captured.get().promptTemplateCode()).isEqualTo("clinic.consultation.structure-notes.v1");
            assertThat(captured.get().useCaseCode()).isEqualTo("consultation_structure_notes");
            assertThat(response.parseStatus()).isEqualTo("VALID");
            assertThat(response.structuredData()).containsEntry("subjective", "Fever and cough.");
            assertThat(response.structuredData()).containsEntry("objective", "BP 138/86, Pulse 96.");
            assertThat(response.structuredData()).containsEntry("assessment", "Viral syndrome.");
            assertThat(response.structuredData()).containsEntry("plan", "Hydration.");
            assertThat(response.structuredData().values()).allSatisfy(value -> {
                String text = String.valueOf(value);
                assertThat(text).doesNotContain("{").doesNotContain("}").doesNotContain("=");
            });
        } finally {
            RequestContextHolder.clear();
        }
    }

    @Test
    void consultationSoapNormalizesNestedStructuredMapsToReadableProse() throws Exception {
        AiOrchestrationService orchestrationService = mock(AiOrchestrationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> structured = Map.of(
                "subjective", Map.of(
                        "presenting_illness", "Patient reports fever, cough, body ache and weakness for 4 days",
                        "symptoms", "fever, cough, body ache and weakness for 4 days",
                        "past_medical_history", "Type 2 Diabetes Mellitus",
                        "allergies", "Paracetamol",
                        "medications", List.of("Metformin 500 mg twice daily", "Cetirizine")
                ),
                "objective", Map.of(
                        "vitals", Map.of(
                                "blood_pressure", "138/86 mmHg",
                                "pulse", "96 bpm",
                                "respiratory_rate", "18 breaths/min",
                                "temperature", "38.4 C",
                                "spo2", "97%",
                                "bmi", "26.8",
                                "random_blood_sugar", "186 mg/dL"
                        ),
                        "observations", "Unknown"
                ),
                "assessment", Map.of(
                        "diagnosis", "Viral syndrome",
                        "summary", "Fever and elevated RBS suggest infection-related hyperglycaemia",
                        "abnormal_findings", List.of("HbA1c 8.4%", "RBS 186 mg/dL")
                ),
                "plan", Map.of(
                        "recommendations", List.of("Continue hydration", "Monitor blood glucose closely"),
                        "follow_up", "Review in 1 week"
                )
        );
        when(orchestrationService.complete(any(AiOrchestrationRequest.class))).thenReturn(orchestrationResponse(structured, objectMapper.writeValueAsString(structured)));

        AiDoctorCopilotService service = new AiDoctorCopilotService(orchestrationService, objectMapper, true);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", java.util.Set.of(), "DOCTOR", "corr"));
        try {
            AiDraftResponse response = service.draft(
                    AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                    "clinic.consultation.structure-notes.v1",
                    "consultation_structure_notes",
                    Map.of("consultationId", UUID.randomUUID(), "patientId", UUID.randomUUID()),
                    List.of()
            );

            assertThat(response.parseStatus()).isEqualTo("VALID");
            assertThat(response.structuredData().get("subjective").toString()).contains("Patient reports fever, cough, body ache and weakness for 4 days.");
            assertThat(response.structuredData().get("subjective").toString()).contains("Known Type 2 Diabetes Mellitus.");
            assertThat(response.structuredData().get("subjective").toString()).contains("Allergy: Paracetamol.");
            assertThat(response.structuredData().get("subjective").toString()).contains("Current medications include Metformin 500 mg twice daily; Cetirizine.");
            assertThat(response.structuredData().get("objective").toString()).contains("BP 138/86 mmHg; pulse 96 bpm; RR 18 breaths/min; temperature 38.4 C; SpO2 97%; BMI 26.8; RBS 186 mg/dL");
            assertThat(response.structuredData().get("assessment").toString()).contains("Viral syndrome.");
            assertThat(response.structuredData().get("assessment").toString()).contains("Fever and elevated RBS suggest infection-related hyperglycaemia.");
            assertThat(response.structuredData().get("assessment").toString()).contains("HbA1c 8.4%; RBS 186 mg/dL");
            assertThat(response.structuredData().get("plan").toString()).contains("Recommendations: Continue hydration; Monitor blood glucose closely.");
            assertThat(response.structuredData().get("plan").toString()).contains("Follow-up: Review in 1 week.");
            assertThat(response.structuredData().values()).allSatisfy(value -> {
                String text = String.valueOf(value);
                assertThat(text).doesNotContain("{").doesNotContain("}").doesNotContain("=").doesNotContain("[object Object]");
                assertThat(text).doesNotContain("Unknown");
            });
            assertThat(response.structuredData().get("subjective").toString()).doesNotContain("fever, cough, body ache and weakness for 4 days Patient reports fever, cough, body ache and weakness for 4 days");
        } finally {
            RequestContextHolder.clear();
        }
    }

    @Test
    void consultationSoapParsesFencedJsonAndAliasKeys() {
        AiOrchestrationService orchestrationService = mock(AiOrchestrationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(orchestrationService.complete(any(AiOrchestrationRequest.class))).thenReturn(orchestrationResponse(
                null,
                """
                Here is the SOAP draft:
                ```json
                {"ChiefComplaint":"Fever for 4 days","Vitals":"BP 138/86, Pulse 96","Impression":"Viral syndrome","ManagementPlan":"Hydration and rest"}
                ```
                """
        ));

        AiDoctorCopilotService service = new AiDoctorCopilotService(orchestrationService, objectMapper, true);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", java.util.Set.of(), "DOCTOR", "corr"));
        try {
            AiDraftResponse response = service.draft(
                    AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                    "clinic.consultation.structure-notes.v1",
                    "consultation_structure_notes",
                    Map.of("consultationId", UUID.randomUUID(), "patientId", UUID.randomUUID()),
                    List.of()
            );

            assertThat(response.parseStatus()).isEqualTo("VALID");
            assertThat(response.structuredData()).containsEntry("subjective", "Patient reports Fever for 4 days.");
            assertThat(response.structuredData()).containsEntry("objective", "BP 138/86, Pulse 96.");
            assertThat(response.structuredData()).containsEntry("assessment", "Viral syndrome.");
            assertThat(response.structuredData()).containsEntry("plan", "Hydration and rest.");
        } finally {
            RequestContextHolder.clear();
        }
    }

    @Test
    void consultationSoapParsesMarkdownAndDropsDisclaimer() {
        AiOrchestrationService orchestrationService = mock(AiOrchestrationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(orchestrationService.complete(any(AiOrchestrationRequest.class))).thenReturn(orchestrationResponse(
                null,
                """
                This is an AI-generated draft. Doctor must verify before use.

                Chief Complaint:
                Fever, cough, body ache for 4 days

                History:
                Diabetic patient with prior similar infections

                Examination / findings:
                BP 138/86, Pulse 96, Resp 18, Temp 38.4 C, SpO2 97%, BMI 26.8

                Assessment:
                Viral syndrome with dehydration risk

                Plan:
                Hydration, rest, monitor blood glucose, review if worsening
                """
        ));

        AiDoctorCopilotService service = new AiDoctorCopilotService(orchestrationService, objectMapper, true);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", java.util.Set.of(), "DOCTOR", "corr"));
        try {
            AiDraftResponse response = service.draft(
                    AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                    "clinic.consultation.structure-notes.v1",
                    "consultation_structure_notes",
                    Map.of("consultationId", UUID.randomUUID(), "patientId", UUID.randomUUID()),
                    List.of()
            );

            assertThat(response.parseStatus()).isEqualTo("VALID");
            assertThat(response.structuredData().get("subjective").toString()).contains("Fever, cough, body ache for 4 days");
            assertThat(response.structuredData().get("subjective").toString()).contains("Diabetic patient with prior similar infections");
            assertThat(response.structuredData().get("objective").toString()).contains("BP 138/86, Pulse 96, Resp 18, Temp 38.4 C, SpO2 97%, BMI 26.8");
            assertThat(response.structuredData().get("assessment").toString()).contains("Viral syndrome with dehydration risk");
            assertThat(response.structuredData().get("plan").toString()).contains("Hydration, rest, monitor blood glucose, review if worsening");
            assertThat(response.structuredData().values()).noneMatch(value -> String.valueOf(value).contains("AI-generated draft"));
            assertThat(response.structuredData().values()).allSatisfy(value -> {
                String text = String.valueOf(value);
                assertThat(text).doesNotContain("{").doesNotContain("}");
            });
        } finally {
            RequestContextHolder.clear();
        }
    }

    @Test
    void consultationSoapRejectsUnrecognizedProseSafely() {
        AiOrchestrationService orchestrationService = mock(AiOrchestrationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(orchestrationService.complete(any(AiOrchestrationRequest.class))).thenReturn(orchestrationResponse(
                null,
                "This is an AI-generated draft. Doctor must verify before use. Please review the patient carefully."
        ));

        AiDoctorCopilotService service = new AiDoctorCopilotService(orchestrationService, objectMapper, true);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", java.util.Set.of(), "DOCTOR", "corr"));
        try {
            AiDraftResponse response = service.draft(
                    AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                    "clinic.consultation.structure-notes.v1",
                    "consultation_structure_notes",
                    Map.of("consultationId", UUID.randomUUID(), "patientId", UUID.randomUUID()),
                    List.of()
            );

            assertThat(response.parseStatus()).isEqualTo("FAILED");
            assertThat(response.structuredData()).isEmpty();
        } finally {
            RequestContextHolder.clear();
        }
    }

    private AiOrchestrationResponse orchestrationResponse(Object structuredJson, String rawText) {
        return orchestrationResponse(structuredJson == null ? null : toJson(structuredJson), rawText);
    }

    private String toJson(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private AiOrchestrationResponse orchestrationResponse(String structuredJson, String rawText) {
        return new AiOrchestrationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiProductCode.CLINIC,
                AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                "GEMINI",
                "gemini-2.5-flash",
                rawText,
                structuredJson,
                BigDecimal.valueOf(0.92),
                List.of(),
                List.of("Review clinically"),
                List.of(),
                new AiTokenUsage(128L, 96L, 224L, BigDecimal.valueOf(0.02)),
                42L,
                false,
                null,
                "STOP",
                "COMPLETE",
                rawText == null ? 0 : rawText.length(),
                rawText,
                "UNKNOWN"
        );
    }
}
